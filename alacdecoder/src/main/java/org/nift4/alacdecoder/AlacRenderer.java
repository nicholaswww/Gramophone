package org.nift4.alacdecoder;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;

@OptIn(markerClass = UnstableApi.class)
public class AlacRenderer extends DecoderAudioRenderer<AlacDecoder> {
    public AlacRenderer(Handler eventHandler, AudioRendererEventListener eventListener, AudioSink audioSink) {
        super(eventHandler, eventListener, audioSink);
    }

    @Override
    protected int supportsFormatInternal(@NonNull Format format) {
        if (!MimeTypes.AUDIO_ALAC.equalsIgnoreCase(format.sampleMimeType)) {
            return C.FORMAT_UNSUPPORTED_TYPE;
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return C.FORMAT_UNSUPPORTED_DRM;
        }
        int bitDepth = format.initializationData.get(0)[5];
        // should we support ENCODING_PCM_20BIT in media3 instead?
        int pcmEncoding = bitDepth == 20 ? C.ENCODING_PCM_24BIT : Util.getPcmEncoding(bitDepth);
        if (!sinkSupportsFormat(
                Util.getPcmFormat(pcmEncoding, format.channelCount, format.sampleRate))) {
            return C.FORMAT_UNSUPPORTED_SUBTYPE;
        }
        return C.FORMAT_HANDLED;
    }

    @NonNull
    @Override
    protected AlacDecoder createDecoder(@NonNull Format format, CryptoConfig cryptoConfig) {
        try {
            return new AlacDecoder(format, 16, 16);
        } catch (AlacDecoderException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    @Override
    protected Format getOutputFormat(@NonNull AlacDecoder decoder) {
        Format format = decoder.getInputFormat();
        int bitDepth = format.initializationData.get(0)[5];
        int pcmEncoding = bitDepth == 20 ? C.ENCODING_PCM_24BIT : Util.getPcmEncoding(bitDepth);
        return Util.getPcmFormat(pcmEncoding, format.channelCount, format.sampleRate);
    }

    @NonNull
    @Override
    public String getName() {
        return "AlacRenderer";
    }
}
