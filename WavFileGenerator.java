package org.example;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;

/*
java WavFileGenerator 5 2 440 880

Dieser Befehl erzeugt eine WAV-Datei mit einer Dauer von 5 Sekunden, zwei Kanälen und Frequenzen von 440 Hz und 880 Hz
für den ersten bzw. zweiten Kanal. Bitte stellen Sie sicher, dass Sie genügend Frequenzen für die Anzahl
der Kanäle angeben. Der obige Code erzeugt 16-Bit-Mono- oder Stereo-WAV-Dateien, abhängig von der Anzahl der Kanäle.
Wenn Sie mehr Kanäle benötigen, müssen Sie den Code entsprechend anpassen.
 */

public class WavFileGenerator {
    public static void main(String[] args) {
        try {
            int sampleRate = 44100; // 44.1 kHz
            double duration = Double.parseDouble(args[0]); // duration in seconds
            int numChannels = Integer.parseInt(args[1]); // number of channels
            int[] frequencies = new int[numChannels]; // frequencies for each channel

            // Parse frequencies from command line arguments
            for (int i = 0; i < numChannels; i++) {
                frequencies[i] = Integer.parseInt(args[i + 2]);
            }

            byte[] buffer = new byte[(int) (sampleRate * duration * 2 * numChannels)];
            for (int i = 0; i < sampleRate * duration; i++) {
                for (int j = 0; j < numChannels; j++) {
                    double angle = 2.0 * Math.PI * i / (sampleRate / frequencies[j]);
                    short amplitude = (short) (Math.sin(angle) * Short.MAX_VALUE);
                    buffer[2 * i * numChannels + 2 * j] = (byte) (amplitude & 0xFF);
                    buffer[2 * i * numChannels + 2 * j + 1] = (byte) ((amplitude >> 8) & 0xFF);
                }
            }

            AudioFormat format = new AudioFormat(sampleRate, 16, numChannels, true, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
            AudioInputStream ais = new AudioInputStream(bais, format, buffer.length / 2);
            File output = new File("output.wav");
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}