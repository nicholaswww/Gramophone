// SPDX-License-Identifier: BSD-2-Clause
// Copyright © 2021 Billy Laws


#include <dlfcn.h>
#include <android/dlext.h>
#include <unistd.h>
#include <sys/mman.h>
#include "android_linker_ns.h"

using loader_android_create_namespace_t = android_namespace_t *(*)(const char *, const char *, const char *, uint64_t, const char *, android_namespace_t *, const void *);
static loader_android_create_namespace_t loader_android_create_namespace;

static bool lib_loaded;

/* Public API */
bool linkernsbypass_load_status() {
	return lib_loaded;
}
struct android_namespace_t *android_create_namespace_escape(const char *name,
                                                            const char *ld_library_path,
                                                            const char *default_library_path,
                                                            uint64_t type,
                                                            const char *permitted_when_isolated_path,
                                                            android_namespace_t *parent_namespace) {
	auto caller{reinterpret_cast<void *>(&dlopen)};
	return loader_android_create_namespace(name, ld_library_path, default_library_path, type,
	                                       permitted_when_isolated_path, parent_namespace, caller);
}

void *linkernsbypass_namespace_dlopen(const char *filename, int flags, android_namespace_t *ns) {
	android_dlextinfo extInfo{
			.flags = ANDROID_DLEXT_USE_NAMESPACE,
			.library_namespace = ns
	};

	return android_dlopen_ext(filename, flags, &extInfo);
}

static void *align_ptr(void *ptr) {
	return reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(ptr) & ~(getpagesize() - 1));
}

/* Private */
__attribute__((constructor)) static void resolve_linker_symbols() {
	using loader_dlopen_t = void *(*)(const char *, int, const void *);

	if (android_get_device_api_level() < 28)
		return;

	// ARM64 specific function walking to locate the internal dlopen handler
	auto loader_dlopen{[]() {
		union BranchLinked {
			uint32_t raw;

			struct {
				int32_t offset : 26; //!< 26-bit branch offset
				uint8_t sig : 6;  //!< 6-bit signature
			};

			[[nodiscard]] bool Verify() const {
				return sig == 0x25;
			}
		};
		static_assert(sizeof(BranchLinked) == 4, "BranchLinked is wrong size");

		// Some devices ship with --X mapping for executables so work around that
		mprotect(align_ptr(reinterpret_cast<void *>(&dlopen)), getpagesize(), PROT_WRITE | PROT_READ | PROT_EXEC);

		// dlopen is just a wrapper for __loader_dlopen that passes the return address as the third arg hence we can just walk it to find __loader_dlopen
		auto blInstr{reinterpret_cast<BranchLinked *>(&dlopen)};
		while (!blInstr->Verify())
			blInstr++;

		return reinterpret_cast<loader_dlopen_t>(blInstr + blInstr->offset);
	}()};

	// Protect the loader_dlopen function to remove the BTI attribute (since this is an internal function that isn't intended to be jumped indirectly to)
	mprotect(align_ptr(reinterpret_cast<void *>(&loader_dlopen)), getpagesize(), PROT_WRITE | PROT_READ | PROT_EXEC);

	// Passing dlopen as a caller address tricks the linker into using the internal unrestricted namespace letting us access libraries that are normally forbidden in the classloader namespace imposed on apps
	auto libdlAndroidHandle{loader_dlopen("libdl_android.so", RTLD_LAZY, reinterpret_cast<void *>(&dlopen))};
	if (!libdlAndroidHandle)
		return;

	loader_android_create_namespace = reinterpret_cast<loader_android_create_namespace_t>(dlsym(libdlAndroidHandle, "__loader_android_create_namespace"));
	if (!loader_android_create_namespace)
		return;

	// Lib is now safe to use
	lib_loaded = true;
}