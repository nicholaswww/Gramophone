package org.nift4.alacdecoder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;

import com.beatofthedrum.alacdecoder.AlacDecodeUtils;
import com.beatofthedrum.alacdecoder.AlacFile;

import java.nio.ByteBuffer;

@OptIn(markerClass = UnstableApi.class)
public class AlacDecoder extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, AlacDecoderException> {
    private static final int ALAC_MAX_PACKET_SIZE = 16384;

    private final Format inputFormat;
    private final AlacFile file;

    public AlacDecoder(Format inputFormat, int numInputBuffers, int numOutputBuffers) throws AlacDecoderException {
        super(new DecoderInputBuffer[numInputBuffers], new SimpleDecoderOutputBuffer[numOutputBuffers]);
        this.inputFormat = inputFormat;
        this.file = AlacDecodeUtils.create_alac(
                Util.getByteDepth(inputFormat.pcmEncoding) * 8, inputFormat.channelCount);
        AlacDecodeUtils.alac_set_info(file, ByteBuffer.wrap(inputFormat.initializationData.get(0)));
        int mp4MaxSize = inputFormat.maxInputSize != Format.NO_VALUE ? inputFormat.maxInputSize
                : ALAC_MAX_PACKET_SIZE;
        setInitialInputBufferSize(Math.min(
                file.bytespersample * file.setinfo_max_samples_per_frame, mp4MaxSize));
    }

    public Format getInputFormat() {
        return inputFormat;
    }

    @NonNull
    @Override
    public String getName() {
        return "AlacDecoder";
    }

    @NonNull
    @Override
    protected DecoderInputBuffer createInputBuffer() {
        return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    }

    @NonNull
    @Override
    protected SimpleDecoderOutputBuffer createOutputBuffer() {
        return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
    }

    @NonNull
    @Override
    protected AlacDecoderException createUnexpectedDecodeException(@NonNull Throwable error) {
        return new AlacDecoderException(error);
    }

    @Nullable
    @Override
    protected AlacDecoderException decode(@NonNull DecoderInputBuffer inputBuffer,
                                          @NonNull SimpleDecoderOutputBuffer outputBuffer,
                                          boolean reset) {
        if (!Util.castNonNull(inputBuffer.data).hasArray())
            return new AlacDecoderException("input has no array");
        if (inputBuffer.hasSupplementalData())
            return new AlacDecoderException("input has extra data, why?");
        try {
            int limit = AlacDecodeUtils.decode_frame(file, inputBuffer, outputBuffer);
            Util.castNonNull(outputBuffer.data).position(0);
            outputBuffer.data.limit(limit);
            return null;
        } catch (AlacDecoderException e) {
            return e;
        } finally {
            file.input_buffer = null;
        }
    }
}
