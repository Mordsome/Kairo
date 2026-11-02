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
