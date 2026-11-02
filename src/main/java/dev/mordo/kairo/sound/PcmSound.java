package dev.mordo.kairo.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decoded audio ready for OpenAL: mono, 16-bit signed little-endian PCM. Mono so it can be positioned in 3D. */
public record PcmSound(short[] samples, int sampleRate) {
	public static final int RATE = 44100;
	/** Longer clips are cut: these are hit and pop sounds, not music. */
	public static final float MAX_SECONDS = 8;

	public float seconds() {
		return samples.length / (float) sampleRate;
	}

	/** A fresh buffer for the sound engine (it keeps it and creates the OpenAL buffer on first play). */
	public SoundBuffer toBuffer() {
		ByteBuffer data = BufferUtils.createByteBuffer(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (short s : samples) {
			data.putShort(s);
		}
		data.flip();
		return new SoundBuffer(data, new AudioFormat(sampleRate, 16, 1, true, false));
	}

	/** From floats in -1..1 (clipped). */
	public static PcmSound fromFloats(float[] wave, int sampleRate) {
		short[] out = new short[wave.length];
		for (int i = 0; i < wave.length; i++) {
			out[i] = (short) Math.round(Math.clamp(wave[i], -1f, 1f) * 32767);
		}
		return new PcmSound(out, sampleRate);
	}
}
