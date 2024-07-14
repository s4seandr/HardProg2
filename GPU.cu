#include <stdio.h>
#include <stdlib.h>
#include <cuda.h>
#include <cufft.h>
#include <math.h>

#define BLOCK_SIZE 1024
#define FFT_SIZE 1024
#define HOP_SIZE 64
#define AMPLITUDE_THRESHOLD 0.1

typedef struct {
    char chunkId[4];
    int chunkSize;
    char format[4];
    char subchunk1Id[4];
    int subchunk1Size;
    short audioFormat;
    short numChannels;
    int sampleRate;
    int byteRate;
    short blockAlign;
    short bitsPerSample;
    char subchunk2Id[4];
    int subchunk2Size;
} WavHeader;

__global__ void convertToDouble(short* input, double* output, int start, int numSamples) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx < numSamples) {
        output[idx] = input[start + idx] / ((double)SHRT_MAX);
    }
}

__global__ void calculateAmplitude(cufftDoubleComplex* input, double* output, int numSamples) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx < numSamples / 2) { // Nur die erste Hälfte der FFT-Ergebnisse ist relevant
        output[idx] = sqrt(input[idx].x * input[idx].x + input[idx].y * input[idx].y) / (numSamples / 2);
    }
}

int main(int argc, char** argv) {
    cudaEvent_t start, stop;
    cudaEventCreate(&start);
    cudaEventCreate(&stop);

    // Startzeit aufzeichnen
    cudaEventRecord(start, 0);

    char* wavFilePath = "C:\\Users\\Sebi\\IdeaProjects\\untitled1\\output.wav";

    FILE* file = fopen(wavFilePath, "rb");
    if (file == NULL) {
        printf("Could not open WAV file\n");
        return 1;
    }

    WavHeader header;
    fread(&header, sizeof(WavHeader), 1, file);

    int numSamples = header.subchunk2Size / 2;
    short* samples = (short*)malloc(numSamples * sizeof(short));
    for (int i = 0; i < numSamples; i++) {
        fread(&samples[i], sizeof(short), 1, file);
    }
    fclose(file);

    short* d_samples;
    cudaMalloc((void**)&d_samples, numSamples * sizeof(short));
    cudaMemcpy(d_samples, samples, numSamples * sizeof(short), cudaMemcpyHostToDevice);

    double* d_samplesDouble;
    cudaMalloc((void**)&d_samplesDouble, FFT_SIZE * sizeof(double));

    cufftHandle plan;
    cufftDoubleComplex* d_fft;
    cudaMalloc((void**)&d_fft, FFT_SIZE * sizeof(cufftDoubleComplex));
    cufftPlan1d(&plan, FFT_SIZE, CUFFT_D2Z, 1);

    double* d_amplitude;
    cudaMalloc((void**)&d_amplitude, FFT_SIZE / 2 * sizeof(double)); // Nur die erste Hälfte ist relevant

    double* sum = (double*)calloc(FFT_SIZE / 2, sizeof(double));
    double* count = (double*)calloc(FFT_SIZE / 2, sizeof(double));

    dim3 dimBlock(BLOCK_SIZE, 1, 1);
    dim3 dimGrid((FFT_SIZE - 1) / BLOCK_SIZE + 1, 1, 1);

    double* amplitude = (double*)malloc(FFT_SIZE / 2 * sizeof(double));

    for (int i = 0; i < numSamples - FFT_SIZE; i += HOP_SIZE) {
        convertToDouble << <dimGrid, dimBlock >> > (d_samples, d_samplesDouble, i, FFT_SIZE);
        cufftExecD2Z(plan, d_samplesDouble, d_fft);

        calculateAmplitude << <dimGrid, dimBlock >> > (d_fft, d_amplitude, FFT_SIZE);
        cudaMemcpy(amplitude, d_amplitude, FFT_SIZE / 2 * sizeof(double), cudaMemcpyDeviceToHost);

        for (int j = 0; j < FFT_SIZE / 2; j++) {
            sum[j] += amplitude[j];
            count[j]++;
        }
    }

    // Stoppzeit aufzeichnen
    cudaEventRecord(stop, 0);
    cudaEventSynchronize(stop);

    float milliseconds = 0;
    cudaEventElapsedTime(&milliseconds, start, stop);

    cudaEventDestroy(start);
    cudaEventDestroy(stop);

    for (int j = 0; j < FFT_SIZE / 2; j++) {
        double average = sum[j] / count[j];
        if (average > AMPLITUDE_THRESHOLD) {
            double frequency = (double)j * header.sampleRate / FFT_SIZE;
            printf("Frequenz: %f Hz, Durchschnittliche Amplitude: %f\n", frequency, average);
        }
    }
    printf("Programm dauerte %f Sekunden.\n", milliseconds / 1000);

    free(samples);
    free(sum);
    free(count);
    free(amplitude);
    cudaFree(d_samples);
    cudaFree(d_samplesDouble);
    cudaFree(d_fft);
    cudaFree(d_amplitude);
    cufftDestroy(plan);

    return 0;
}
