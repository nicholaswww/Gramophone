/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// last observed AOSP size (arm64) was 1312
#define AUDIO_TRACK_SIZE 5000
// last observed AOSP size (arm64) was 152
#define ATTRIBUTION_SOURCE_SIZE 500
#define FAKE_PTR_SIZE 32

// make sure to call RefBase::incStrong on thePtr before giving fake_sp away, and call
// decStrong when you don't need it anymore :)
struct fake_sp {
    void* /* MUST be RefBase* (or compatible contract, but ymmv) */ thePtr;
};
// make sure to call RefBase::createWeak(thePtr, <arbitrary unique id>) and save returned pointer
// to refs, then call RefBase::weakref_type::decWeak(refs) when you don't need it anymore
struct fake_wp {
    void* /* MUST be RefBase* */ thePtr;
    void* /* RefBase::weakref_type* */ refs;
};
enum transfer_type {
    TRANSFER_DEFAULT,   // not specified explicitly; determine from the other parameters
    TRANSFER_CALLBACK,  // callback EVENT_MORE_DATA
    TRANSFER_OBTAIN,    // call obtainBuffer() and releaseBuffer()
    TRANSFER_SYNC,      // synchronous write()
    TRANSFER_SHARED,    // shared memory
    TRANSFER_SYNC_NOTIF_CALLBACK, // synchronous write(), notif EVENT_CAN_WRITE_MORE_DATA
};
/*
namespace android {
    // NOLINTBEGIN
    class RefBase {
    public:
        inline RefBase() {
            ALOGI("fake base impl of ctor says hello");
            ZN7android7RefBaseC2Ev(this);
        }

        virtual inline ~RefBase() {
            ALOGI("fake base impl of dtor says hello");
            ZN7android7RefBaseD2Ev(this);
        }

        virtual void onFirstRef();

        virtual void onLastStrongRef(const void *id);

        virtual bool onIncStrongAttempted(uint32_t flags, const void *id);

        virtual void onLastWeakRef(const void *id);

        void *mRefs;
    };
    // NOLINTEND
    class AudioTimestamp {
    public:
        AudioTimestamp() : mPosition(0), mTime({ .tv_sec = 0, .tv_nsec = 0 }) {
            ALOGE("if you see this, expect a segfault. this class AudioTimestamp never was supposed to be instantiated");
        }
        uint32_t mPosition;
        struct timespec mTime;
    };
    class AudioTrack {
    public:
        AudioTrack() {
            ALOGE("if you see this, expect a segfault. this class AudioTrack never was supposed to be instantiated");
        }
        class Buffer
        {
        public:
            Buffer() {
                ALOGE("if you see this, expect a segfault. this class Buffer never was supposed to be instantiated");
            }
            [[nodiscard]] size_t size() const { return mSize; }
            [[nodiscard]] size_t getFrameCount() const { return frameCount; }
            [[nodiscard]] uint8_t * data() const { return ui8; }
            size_t      frameCount = 0;
        private:
            size_t      mSize = 0;
            union {
                void*       raw = nullptr;
                int16_t*    i16;
                uint8_t*    ui8;
            };
            uint32_t    sequence = 0;
        };
        class IAudioTrackCallback : public virtual RefBase {
            friend AudioTrack;
        public:
            IAudioTrackCallback() {
                ALOGI("hi from IAudioTrackCallback ctor");
            }
        protected:
            // This event only occurs for TRANSFER_CALLBACK.
            virtual inline size_t
            onMoreData([[maybe_unused]] const AudioTrack::Buffer &buffer) {
                ALOGE("stub IAudioTrackCallback::onMoreData called");
                return 0;
            }

            virtual inline void onUnderrun() {
                ALOGE("stub IAudioTrackCallback::onUnderrun called");
            }

            virtual inline void onLoopEnd([[maybe_unused]] int32_t loopsRemaining) {
                ALOGE("stub IAudioTrackCallback::onLoopEnd called");
            }

            virtual inline void onMarker([[maybe_unused]] uint32_t markerPosition) {
                ALOGE("stub IAudioTrackCallback::onMarker called");
            }

            virtual inline void onNewPos([[maybe_unused]] uint32_t newPos) {
                ALOGE("stub IAudioTrackCallback::onNewPos called");
            }

            virtual inline void onBufferEnd() {
                ALOGE("stub IAudioTrackCallback::onBufferEnd called");
            }

            virtual inline void onNewIAudioTrack() {
                ALOGE("stub IAudioTrackCallback::onNewIAudioTrack called");
            }

            virtual inline void onStreamEnd() {
                ALOGE("stub IAudioTrackCallback::onStreamEnd called");
            }

            virtual inline void onNewTimestamp([[maybe_unused]] AudioTimestamp timestamp) {
                ALOGE("stub IAudioTrackCallback::onNewTimestamp called");
            }

            // This event only occurs for TRANSFER_SYNC_NOTIF_CALLBACK.
            virtual inline size_t onCanWriteMoreData([[maybe_unused]] const AudioTrack::Buffer &buffer) {
                ALOGE("stub IAudioTrackCallback::onCanWriteMoreData called");
                return 0;
            }
        };
    };
}

inline void android::RefBase::onFirstRef()
{
    ALOGI("fake base impl of onFirstRef says hello");
}

inline void android::RefBase::onLastStrongRef(const void*)
{
    ALOGI("fake base impl of onLastStrongRef says hello");
}

inline bool android::RefBase::onIncStrongAttempted(uint32_t flags, const void*)
{
    ALOGI("fake base impl of onIncStrongAttempted says hello");
    return flags & 1;
}

inline void android::RefBase::onLastWeakRef(const void*)
{
    ALOGI("fake base impl of onLastWeakRef says hello");
}*/