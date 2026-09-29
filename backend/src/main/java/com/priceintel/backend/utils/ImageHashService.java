package com.priceintel.backend.utils;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Base64;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Perceptual image hashing (average hash / aHash) for image-based product
 * search. Produces a 64-bit fingerprint from an image; two images are similar
 * when their fingerprints differ in few bits (Hamming distance). Pure JDK —
 * no external service. Accepts data: URIs and (best-effort) http(s) URLs.
 */
@Slf4j
@Service
public class ImageHashService {

    private static final int SIZE = 8; // 8x8 → 64-bit hash

    /** Compute the aHash of an image source; empty if it can't be decoded. */
    public Optional<String> hash(String imageSrc) {
        byte[] bytes = decode(imageSrc);
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                return Optional.empty();
            }
            BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.drawImage(img, 0, 0, SIZE, SIZE, null);
            g.dispose();

            int[] lum = new int[SIZE * SIZE];
            long sum = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int rgb = scaled.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    int gray = (r + gr + b) / 3;
                    lum[y * SIZE + x] = gray;
                    sum += gray;
                }
            }
            long avg = sum / (SIZE * SIZE);
            long h = 0;
            for (int i = 0; i < lum.length; i++) {
                h = (h << 1) | (lum[i] >= avg ? 1 : 0);
            }
            return Optional.of(String.format("%016x", h));
        } catch (Exception e) {
            log.debug("Could not hash image: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Hamming distance (0..64) between two hex hashes; lower = more similar. */
    public int distance(String hexA, String hexB) {
        if (hexA == null || hexB == null) {
            return 64;
        }
        try {
            long a = Long.parseUnsignedLong(hexA, 16);
            long b = Long.parseUnsignedLong(hexB, 16);
            return Long.bitCount(a ^ b);
        } catch (NumberFormatException e) {
            return 64;
        }
    }

    /** Similarity percentage from a Hamming distance. */
    public int similarityPct(int distance) {
        return Math.round((64 - distance) / 64f * 100f);
    }

    // ---------- helpers ----------

    private byte[] decode(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        try {
            if (src.startsWith("data:")) {
                int comma = src.indexOf(',');
                String b64 = comma >= 0 ? src.substring(comma + 1) : src;
                return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
            }
            if (src.startsWith("http://") || src.startsWith("https://")) {
                try (var in = URI.create(src).toURL().openStream()) {
                    return in.readAllBytes();
                }
            }
            // assume raw base64
            return Base64.getDecoder().decode(src.replaceAll("\\s", ""));
        } catch (Exception e) {
            log.debug("Could not decode image source: {}", e.getMessage());
            return null;
        }
    }
}
