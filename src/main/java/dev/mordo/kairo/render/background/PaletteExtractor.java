package dev.mordo.kairo.render.background;

import dev.mordo.kairo.render.ColorScience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks 4 to 6 theme colours from a picture, like Android's Material You.
 *
 * <ol>
 *     <li>Quantize: pixels go into a 32x32x32 colour histogram, then k-means groups the filled bins in CIELAB.</li>
 *     <li>Score: each group scores higher the more of the picture its hue covers and the more colourful it is
 *     (the idea comes from Material Color Utilities' "Score"; no code was copied). Near-greys only count when the
 *     whole picture is grey.</li>
 *     <li>Choose: the best scores with clearly different hues, then made readable as an accent on dark panels
 *     (lightness kept between 62 and 82, hue kept).</li>
 * </ol>
 */
public final class PaletteExtractor {
	public static final int MAX_SWATCHES = 6;
	private static final int MIN_SWATCHES = 4;
	private static final int CLUSTERS = 24;
	/** Below this chroma a colour counts as grey. */
	private static final double GREY_CHROMA = 12;

	private PaletteExtractor() {
	}

	private record Cluster(double l, double a, double b, double chroma, double hue, double share) {
	}

	/** Swatches from ARGB pixels (any size; sampled down), best first. Empty if there are no opaque pixels. */
	public static List<Integer> extract(int[] argb) {
		// 1. Histogram, 5 bits per channel; transparent pixels skipped. At most ~40k samples.
		int step = Math.max(1, argb.length / 40_000);
		int[] count = new int[32768];
		long[] sumR = new long[32768];
		long[] sumG = new long[32768];
		long[] sumB = new long[32768];
		int total = 0;
		for (int i = 0; i < argb.length; i += step) {
			int c = argb[i];
			if ((c >>> 24) < 128) {
				continue;
			}
			int r = c >> 16 & 0xFF;
			int g = c >> 8 & 0xFF;
			int b = c & 0xFF;
			int bin = (r >> 3) << 10 | (g >> 3) << 5 | (b >> 3);
			count[bin]++;
			sumR[bin] += r;
			sumG[bin] += g;
			sumB[bin] += b;
			total++;
		}
		if (total == 0) {
			return List.of();
		}
		List<double[]> points = new ArrayList<>(); // L, a, b, weight
		for (int bin = 0; bin < count.length; bin++) {
			if (count[bin] == 0) {
				continue;
			}
			int n = count[bin];
			int rgb = 0xFF000000 | (int) (sumR[bin] / n) << 16 | (int) (sumG[bin] / n) << 8 | (int) (sumB[bin] / n);
			double[] lab = ColorScience.lab(rgb);
			points.add(new double[]{lab[0], lab[1], lab[2], n});
		}

		// 2. Weighted k-means in Lab, seeded with the heaviest bin and then the bins furthest from every seed so far.
		int k = Math.min(CLUSTERS, points.size());
		double[][] centers = new double[k][];
		points.sort(Comparator.comparingDouble((double[] p) -> -p[3]));
		centers[0] = points.getFirst().clone();
		double[] nearest = new double[points.size()];
		java.util.Arrays.fill(nearest, Double.MAX_VALUE);
		for (int c = 1; c < k; c++) {
			int far = 0;
			double farScore = -1;
			for (int i = 0; i < points.size(); i++) {
				nearest[i] = Math.min(nearest[i], dist(points.get(i), centers[c - 1]));
				double score = nearest[i] * Math.sqrt(points.get(i)[3]);
				if (score > farScore) {
					farScore = score;
					far = i;
				}
			}
			centers[c] = points.get(far).clone();
		}
		int[] owner = new int[points.size()];
		for (int iteration = 0; iteration < 12; iteration++) {
			double[][] sums = new double[k][4];
			boolean moved = false;
			for (int i = 0; i < points.size(); i++) {
				double[] p = points.get(i);
				int best = 0;
				double bestD = Double.MAX_VALUE;
				for (int c = 0; c < k; c++) {
					double d = dist(p, centers[c]);
					if (d < bestD) {
						bestD = d;
						best = c;
					}
				}
				if (owner[i] != best) {
					owner[i] = best;
					moved = true;
				}
				double w = p[3];
				sums[best][0] += p[0] * w;
				sums[best][1] += p[1] * w;
				sums[best][2] += p[2] * w;
				sums[best][3] += w;
			}
			for (int c = 0; c < k; c++) {
				if (sums[c][3] > 0) {
					centers[c] = new double[]{sums[c][0] / sums[c][3], sums[c][1] / sums[c][3], sums[c][2] / sums[c][3], sums[c][3]};
				} else {
					centers[c][3] = 0;
				}
			}
			if (!moved && iteration > 0) {
				break;
			}
		}
		List<Cluster> clusters = new ArrayList<>();
		for (double[] c : centers) {
			if (c[3] <= 0) {
				continue;
			}
			double chroma = Math.hypot(c[1], c[2]);
			double hue = Math.toDegrees(Math.atan2(c[2], c[1]));
			clusters.add(new Cluster(c[0], c[1], c[2], chroma, hue < 0 ? hue + 360 : hue, c[3] / total));
		}

		// 3. Score.
		List<Cluster> colourful = clusters.stream().filter(c -> c.chroma() >= GREY_CHROMA && c.share() >= 0.004).toList();
		if (colourful.isEmpty()) {
			return greys(clusters);
		}
		List<double[]> scored = new ArrayList<>(); // index, score
		for (int i = 0; i < colourful.size(); i++) {
			Cluster c = colourful.get(i);
			// How much of the picture has (about) this hue.
			double hueShare = 0;
			for (Cluster other : colourful) {
				if (ColorScience.hueDistance(c.hue(), other.hue()) <= 15) {
					hueShare += other.share();
				}
			}
			double score = hueShare * 100 * 0.7 + (c.chroma() - 48) * (c.chroma() < 48 ? 0.1 : 0.3);
			scored.add(new double[]{i, score});
		}
		scored.sort(Comparator.comparingDouble((double[] s) -> -s[1]));

		// 4. Choose distinct hues, loosening the rule until there are enough.
		List<Cluster> chosen = new ArrayList<>();
		for (double minHue : new double[]{40, 25, 12}) {
			for (double[] s : scored) {
				Cluster c = colourful.get((int) s[0]);
				if (chosen.size() >= MAX_SWATCHES || chosen.contains(c)) {
					continue;
				}
				boolean distinct = chosen.stream().allMatch(o -> ColorScience.hueDistance(o.hue(), c.hue()) >= minHue);
				if (distinct) {
					chosen.add(c);
				}
			}
			if (chosen.size() >= MIN_SWATCHES) {
				break;
			}
		}
		List<Integer> swatches = new ArrayList<>();
		for (Cluster c : chosen) {
			addDistinct(swatches, accentReady(c.l(), c.chroma(), c.hue()));
		}
		// A picture with only one or two hues: lighter and deeper versions of its main colour fill the row.
		Cluster main = chosen.getFirst();
		double[] tones = {74, 64, 82, 58, 86};
		for (int i = 0; swatches.size() < MIN_SWATCHES && i < tones.length; i++) {
			addDistinct(swatches, ColorScience.fromLch(tones[i], Math.max(main.chroma(), 30), main.hue()));
		}
		return swatches;
	}

	/** A grey picture: its greys (and a slightly tinted grey if the picture leans that way), at accent lightness. */
	private static List<Integer> greys(List<Cluster> clusters) {
		List<Cluster> byShare = new ArrayList<>(clusters);
		byShare.sort(Comparator.comparingDouble(c -> -c.share()));
		List<Integer> swatches = new ArrayList<>();
		for (Cluster c : byShare) {
			if (swatches.size() >= MIN_SWATCHES) {
				break;
			}
			addDistinct(swatches, accentReady(c.l(), c.chroma(), c.hue()));
		}
		double[] tones = {72, 62, 82, 90};
		for (int i = 0; swatches.size() < MIN_SWATCHES && i < tones.length; i++) {
			addDistinct(swatches, ColorScience.fromLch(tones[i], 0, 0));
		}
		return swatches;
	}

	/** The colour as an accent: same hue, lightness 62 to 82 (readable on dark panels), chroma kept. */
	static int accentReady(double l, double chroma, double hue) {
		return ColorScience.fromLch(Math.clamp(l, 62, 82), chroma, hue);
	}

	/** Adds the colour unless an almost identical one is already there. */
	private static void addDistinct(List<Integer> swatches, int color) {
		double[] lab = ColorScience.lab(color);
		for (int existing : swatches) {
			double[] o = ColorScience.lab(existing);
			double d = Math.sqrt(sq(lab[0] - o[0]) + sq(lab[1] - o[1]) + sq(lab[2] - o[2]));
			if (d < 6) {
				return;
			}
		}
		swatches.add(color);
	}

	private static double dist(double[] p, double[] c) {
		return sq(p[0] - c[0]) + sq(p[1] - c[1]) + sq(p[2] - c[2]);
	}

	private static double sq(double v) {
		return v * v;
	}
}
