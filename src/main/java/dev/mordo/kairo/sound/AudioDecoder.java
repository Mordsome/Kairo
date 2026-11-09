package dev.mordo.kairo.sound;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.sounds.JOrbisAudioStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a sound file into {@link PcmSound}: OGG Vorbis through Minecraft's own decoder, WAV / AIFF / AU through
 * Java's audio API. Everything is mixed down to mono and cut at {@link PcmSound#MAX_SECONDS}.
 */
public final class AudioDecoder {
	public static final Set<String> EXTENSIONS = Set.of("ogg", "wav", "aiff", "aif", "au");

	private AudioDecoder() {
	}

	public static boolean isSupported(Path file) {
		return EXTENSIONS.contains(extension(file));
	}

	public static String extension(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	public static PcmSound decode(Path file) throws IOException {
		String ext = extension(file);
		if (ext.equals("mp3")) {
			throw new IOException("MP3 isn't supported; convert it to OGG or WAV");
		}
		if (ext.equals("ogg")) {
			return decodeOgg(file);
		}
		return decodeSampled(file);
	}

	private static PcmSound decodeOgg(Path file) throws IOException {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
			 JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
			AudioFormat format = stream.getFormat();
			int channels = Math.max(1, format.getChannels());
			int rate = Math.round(format.getSampleRate());
			int limit = Math.round(PcmSound.MAX_SECONDS * rate) * channels;
			FloatArrayList interleaved = new FloatArrayList();
			while (interleaved.size() < limit && stream.readChunk(interleaved::add)) {
				// keep reading
			}
			return mono(interleaved.toFloatArray(), channels, rate, limit);
		}
	}

	private static PcmSound decodeSampled(Path file) throws IOException {
		try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
			AudioFormat in = source.getFormat();
			int channels = Math.max(1, in.getChannels());
			AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, in.getSampleRate(), 16, channels, channels * 2, in.getSampleRate(), false);
			try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, source)) {
				int rate = Math.round(in.getSampleRate());
				int limit = Math.round(PcmSound.MAX_SECONDS * rate) * channels;
				byte[] bytes = converted.readNBytes(limit * 2);
				float[] interleaved = new float[bytes.length / 2];
				for (int i = 0; i < interleaved.length; i++) {
					short s = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
					interleaved[i] = s / 32768f;
				}
				return mono(interleaved, channels, rate, limit);
			}
		} catch (javax.sound.sampled.UnsupportedAudioFileException e) {
			throw new IOException("Unsupported audio format", e);
		}
	}

	/**
	 * A whole song (music emotes): mixed down to mono while it's read, straight into 16-bit samples, so even a long
	 * stereo file only takes about 5 MB a minute. Cut at {@code maxSeconds}.
	 */
	public static PcmSound decodeSong(Path file, float maxSeconds) throws IOException {
		String ext = extension(file);
		if (ext.equals("mp3")) {
			throw new IOException("MP3 isn't supported; convert it to OGG or WAV");
		}
		if (ext.equals("ogg")) {
			try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
				 JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
				AudioFormat format = stream.getFormat();
				int channels = Math.max(1, format.getChannels());
				int rate = Math.round(format.getSampleRate());
				MonoSink sink = new MonoSink(channels, Math.round(maxSeconds * rate));
				while (!sink.full() && stream.readChunk(sink::add)) {
					// keep reading
				}
				return sink.finish(rate);
			}
		}
		try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
			AudioFormat in = source.getFormat();
			int channels = Math.max(1, in.getChannels());
			AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, in.getSampleRate(), 16, channels, channels * 2, in.getSampleRate(), false);
			try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, source)) {
				int rate = Math.round(in.getSampleRate());
				MonoSink sink = new MonoSink(channels, Math.round(maxSeconds * rate));
				byte[] chunk = new byte[channels * 2 * 4096];
				int n;
				while (!sink.full() && (n = converted.readNBytes(chunk, 0, chunk.length)) > 0) {
					for (int i = 0; i + 1 < n; i += 2) {
						sink.add((short) ((chunk[i] & 0xFF) | (chunk[i + 1] << 8)) / 32768f);
					}
				}
				return sink.finish(rate);
			}
		} catch (javax.sound.sampled.UnsupportedAudioFileException e) {
			throw new IOException("Unsupported audio format", e);
		}
	}

	/** Averages interleaved channels into a growing array of 16-bit mono samples. */
	private static final class MonoSink {
		private final int channels;
		private final int maxFrames;
		private short[] out = new short[1 << 16];
		private int frames;
		private float sum;
		private int channel;

		MonoSink(int channels, int maxFrames) {
			this.channels = channels;
			this.maxFrames = maxFrames;
		}

		boolean full() {
			return frames >= maxFrames;
		}

		void add(float sample) {
			if (full()) {
				return;
			}
			sum += sample;
			if (++channel < channels) {
				return;
			}
			if (frames == out.length) {
				out = java.util.Arrays.copyOf(out, Math.min(maxFrames, out.length * 2));
			}
			out[frames++] = (short) Math.round(Math.clamp(sum / channels, -1f, 1f) * 32767);
			sum = 0;
			channel = 0;
		}

		PcmSound finish(int rate) throws IOException {
			if (frames == 0) {
				throw new IOException("The file has no audio");
			}
			return new PcmSound(java.util.Arrays.copyOf(out, frames), rate);
		}
	}

	private static PcmSound mono(float[] interleaved, int channels, int rate, int limit) throws IOException {
		int frames = Math.min(interleaved.length, limit) / channels;
		if (frames == 0) {
			throw new IOException("The file has no audio");
		}
		float[] out = new float[frames];
		for (int f = 0; f < frames; f++) {
			float sum = 0;
			for (int c = 0; c < channels; c++) {
				sum += interleaved[f * channels + c];
			}
			out[f] = sum / channels;
		}
		// Short fade at the end of cut clips so they don't click.
		int fade = Math.min(frames, rate / 100);
		for (int i = 0; i < fade; i++) {
			out[frames - 1 - i] *= i / (float) fade;
		}
		return PcmSound.fromFloats(out, rate);
	}
}
