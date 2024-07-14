
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WavFileReader {
    public static void main(String[] args) {
        try {
            // Startzeit für die Zeitmessung
            long startTime = System.nanoTime();

            // Datei einlesen
            File file = new File(args[3]);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioInputStream.getFormat();

            // Audio-Daten in ein Byte-Array einlesen
            long frameLength = audioInputStream.getFrameLength();
            byte[] audioData = new byte[(int) (format.getFrameSize() * frameLength)];
            audioInputStream.read(audioData);

            // Umwandlung der Audio-Daten in ein Array von Samples
            double[] samples = new double[audioData.length / 2];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = ((audioData[2 * i] & 0xFF) | (audioData[2 * i + 1] << 8)) / ((double) Short.MAX_VALUE);
            }

            // Blockgröße, Versatz und Schwellenwert aus den Befehlszeilenargumenten einlesen
            int blockSize = Integer.parseInt(args[0]);
            int hopSize = Integer.parseInt(args[1]);
            double threshold = Double.parseDouble(args[2]);

            FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

            // Maps zur Speicherung der Summe und Anzahl der Amplituden für jede Frequenz
            Map<Double, Double> amplitudeSum = new HashMap<>();
            Map<Double, Integer> amplitudeCount = new HashMap<>();

            // Durchlaufen der Audiodaten in Blöcken
            for (int i = 0; i < samples.length - blockSize; i += hopSize) {
                double[] block = Arrays.copyOfRange(samples, i, i + blockSize);

                // Durchführung der FFT auf dem Block
                Complex[] fft = transformer.transform(block, TransformType.FORWARD);

                // Berechnung der Amplitudenmittelwerte für jede Frequenz
                for (int j = 0; j < fft.length / 2; j++) {
                    double frequency = j * format.getSampleRate() / fft.length;
                    double amplitude = fft[j].abs() / (fft.length / 2);

                    // Aktualisierung der Summe und Anzahl der Amplituden für diese Frequenz
                    amplitudeSum.put(frequency, amplitudeSum.getOrDefault(frequency, 0.0) + amplitude);
                    amplitudeCount.put(frequency, amplitudeCount.getOrDefault(frequency, 0) + 1);
                }
            }

            // Ausgabe der durchschnittlichen Amplitude für jede Frequenz
            for (Double frequency : amplitudeSum.keySet()) {
                double averageAmplitude = amplitudeSum.get(frequency) / amplitudeCount.get(frequency);
                if (averageAmplitude > threshold) {
                    System.out.println("Frequenz: " + frequency + " Hz, Durchschnittliche Amplitude: " + averageAmplitude);
                }
            }

            // Ende der Zeitmessung und Ausgabe der Programmlaufzeit
            long endTime = System.nanoTime();
            double duration = (endTime - startTime) / 1e9;
            System.out.println("Programm dauerte " + duration + " Sekunden.");

        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
    }
}
