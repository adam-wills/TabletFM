#include <jni.h>
#include <cmath>
#include "ece420_main.h"
#include "ece420_lib.h"
#include <cstdint>

// JNI Function
extern "C" {
    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_writeNewFreq(JNIEnv *env, jclass, jdouble);

    // JNI Function to set the wavetable on startup
    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_initTable(JNIEnv *env, jclass, jint);

    // JNI Function to set the modulation envelope on startup
    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_initModFactor(JNIEnv *env, jclass, jdouble);
}



enum WaveType {SINE = 0, SQUARE = 1, SAW = 2, TRIANGLE = 3};
WaveType carWave = SINE, modWave = SINE;

// wavetable space - holds a simple sin wave
float sineVec[TABLE_SIZE+1];

// hopefully holds fourier series approximations of a square wave; the bottom table (idx 0) is a
// pure sinewave (for frequencies approaching the nyquist frequency of 24000Hz); each subsequent
// table doubles the number of partials
float sineTable[NUM_TABLES][TABLE_SIZE+1];
float squareTable[NUM_TABLES][TABLE_SIZE+1];
float sawTable[NUM_TABLES][TABLE_SIZE+1];
float triTable[NUM_TABLES][TABLE_SIZE+1];

float (*tableRefs[4])[NUM_TABLES][TABLE_SIZE+1] = {&sineTable,&squareTable,&sawTable,&triTable};

// ADSR envelopes for carrier and modulating oscillators
std::vector<ADSR> carrierADSR (NUM_VOICES);
std::vector<ADSR> modADSR (NUM_VOICES);

uint8_t oldOscIdx = 1; // may use for polyphony
uint8_t newOscIdx = 0;

float modIdxMax = 2.0;
float modIdxMin = 1.0;
double modToCarRatio = 2.0;

uint16_t modTableIdx[NUM_VOICES] = {0};
uint16_t carrierTableIdx[NUM_VOICES] = {0};

// We have two variables here to ensure that we never change the desired frequency while
// processing a frame. Thread synchronization, etc. These will hold the carrier frequency
double FREQ_NEW_ANDROID = 0;
double FREQ_NEW = 0;

//screen-touch pressure ported in through JNI


// frequency registers (modified on a new key press)
double carFreqNormal = 0.;
double modFreqNormal = 0. ;
//int32_t car_freq[NUM_VOICES] = {(int32_t) ((FREQ_NEW * FS_INV) * (1 << FIXED_SHIFT)),0};
//int32_t mod_freq[NUM_VOICES] = {(int32_t) ((FREQ_NEW * FS_INV) * (1 << FIXED_SHIFT)),0};
int32_t car_freq[NUM_VOICES] = {(int32_t) ((FREQ_NEW * FS_INV) * (1 << FIXED_SHIFT))};
int32_t mod_freq[NUM_VOICES] = {(int32_t) ((FREQ_NEW * FS_INV) * (1 << FIXED_SHIFT))};

// phase registers
//int32_t car_phase[NUM_VOICES] = {0,0};
//int32_t mod_phase[NUM_VOICES] = {0,0};
double carPhaseNormal = 0.;
double modPhaseNormal = 0;
int32_t car_phase[NUM_VOICES] = {0};
int32_t mod_phase[NUM_VOICES] = {0};


// intermediate calculation variables
float mod_out = 0;
float sig_out = 0.f;

// implement our FM synthesis algorithm right here
void FMSynthesis(int16_t* bufferOut,
                 float (*modTable)[NUM_TABLES][TABLE_SIZE+1],
                 float (*carTable)[NUM_TABLES][TABLE_SIZE+1]) {

    // if the carrier frequency is 0 and release flag indicates no note being played
    bool newNote = (FREQ_NEW != 0);
    bool soundOff = true;
    if (!newNote) {
        //bool soundOff = true;
        for (int i = 0; i < NUM_VOICES; i++) {
            soundOff = soundOff && (carrierADSR[i].getValue() < 0.0001);
        }

        if (soundOff) {
            for (int i = 0; i < FRAME_SIZE; i++) {
                bufferOut[i] = (int16_t)0;
            }
            for (int j = 0; j < NUM_VOICES; j++) {
                car_phase[j] = 0;
                mod_phase[j] = 0;
            }
        }
    }
    if (newNote || !soundOff) {
        for (uint32_t i = 0; i < FRAME_SIZE; i++) {
            sig_out = 0.f;
            for (uint8_t j = 0; j < NUM_VOICES; j++) {

                sig_out += (1./NUM_VOICES)*tableInterp(carTable,
                                                       car_phase[j],
                                                       carFreqNormal)*carrierADSR[j].getValue();
                //update the mod phase
                mod_phase[j] += mod_freq[j];
                mod_phase[j] &= DECIMAL_MASK;
                modADSR[j].incrementTime();

                //update the modulation output with these new register values
                mod_out = AMP*tableInterp(modTable,mod_phase[j],modFreqNormal);

                //update carrier phase with new mod output
                car_phase[j] += car_freq[j];
                car_phase[j] &= DECIMAL_MASK;
                //car_phase[j] += (int32_t) modFactor * car_freq[j] * mod_out;
                //float preshMod = (1.-modADSR[j].getValue())*pressure*(modIdxMax-modIdxMin);
                car_phase[j] += (int32_t)(mod_out*(modADSR[j].getValue()*(modIdxMax-modIdxMin) + modIdxMin));
                car_phase[j] &= DECIMAL_MASK;
                // update ADSR value
                carrierADSR[j].incrementTime();
            }
            bufferOut[i] = (int16_t)(AMP*sig_out);
        }
    }
}

void ece420ProcessFrame(sample_buf *dataBuf) {
    int16_t bufferOut[FRAME_SIZE] = {};

    //check if a different key has been pressed or if the pressed key has been released
    if(FREQ_NEW_ANDROID != FREQ_NEW){
        if(FREQ_NEW_ANDROID != 0){

            carFreqNormal = FREQ_NEW_ANDROID*FS_INV;
            modFreqNormal = modToCarRatio*carFreqNormal;

            car_freq[newOscIdx] = (int32_t)(carFreqNormal*(double)(1<<FIXED_SHIFT));
            mod_freq[newOscIdx] = (int32_t)(modFreqNormal*(double)(1<<FIXED_SHIFT));

            carrierADSR[newOscIdx].resetTime();
            modADSR[newOscIdx].resetTime();


        }
        else {
            carrierADSR[newOscIdx].sustainOff();
            modADSR[newOscIdx].sustainOff();

        }
        FREQ_NEW = FREQ_NEW_ANDROID;
        //oldOscIdx = newOscIdx;
        //newOscIdx = (oldOscIdx+1)%2;
    }

    FMSynthesis(bufferOut, tableRefs[(int)modWave], tableRefs[(int)carWave]);

    for (int i = 0; i < FRAME_SIZE; i++) {
        //int16_t newVal = (int16_t) bufferOut[i];
        int16_t newVal = bufferOut[i];
        auto lowByte = (uint8_t) (0x00ff & newVal);
        auto highByte = (uint8_t) ((0xff00 & newVal) >> 8);
        dataBuf->buf_[i * 2] = lowByte;
        dataBuf->buf_[i * 2 + 1] = highByte;
    }

}


// linear interpolation between two adjacent sample values in the same table
// (currently not in use)
float sampleInterp(float (*table)[NUM_TABLES][TABLE_SIZE+1], uint32_t tIdx, double phRegNorm) {
    double sInterp, sampleIdx;
    sInterp = modf(phRegNorm, &sampleIdx);
    int sIdx = ((int)sampleIdx)%TABLE_SIZE;
    float l = (*table)[tIdx][sIdx];
    float r = (*table)[tIdx][sIdx+1];
    return l + sInterp*(r-l);
}


// interpolation between samples from two adjacent frequency tables of the same waveform
float tableInterp(float (*table)[NUM_TABLES][TABLE_SIZE+1], int32_t phReg, double freqRegNorm) {
    double fLevel, tableInterp, tableIdx;
    fLevel = -log2(freqRegNorm*0.25);
    tableInterp = modf(fLevel, &tableIdx);

    auto tIdx = (uint32_t)tableIdx;

    float LV = 0.f;
    float HV = 0.f;
    int32_t sIdx = (phReg >> (FIXED_SHIFT-TABLE_BITS));
    if (fLevel < 0.0) {
        HV = (*table)[tIdx][sIdx];
    }
    else if (fLevel < NUM_TABLES-1) {
        LV = (*table)[tIdx][sIdx];
        HV = (*table)[tIdx+1][sIdx];
    }
    else {
        // trick that uses oversampling to simulate a wavetable with 2^i times the harmonic content by
        // indexing through it 2^i times as quickly, where i is the difference between the
        // highest-numbered wavetable (the one with the most harmonic content used for the lowest pitches)
        // and the wavetable given by the fLevel trick above.
        uint32_t octaveCorrect = tIdx-NUM_TABLES+1;
        sIdx = (sIdx << (tIdx-NUM_TABLES+1)) & DECIMAL_MASK;

        LV = (*table)[NUM_TABLES-1][sIdx%(TABLE_SIZE+1)];
        HV = (*table)[NUM_TABLES-1][(sIdx << 1)%(TABLE_SIZE+1)];
    }
    return LV + tableInterp*(HV-LV);
}


extern "C" {
    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_writeNewFreq(JNIEnv *env, jclass, jdouble newFreq) {
        FREQ_NEW_ANDROID = (double) newFreq;
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_initTable(JNIEnv *env, jclass, jint wave) {
        // table for highest frequencies (lowest table index) is a sinewave
        for (int i = 0; i < TABLE_SIZE+1; i++){
            squareTable[0][i] = sineVec[i];
            sawTable[0][i] = sineVec[i];
            triTable[0][i] = sineVec[i];
        }
        // zero out remaining tables;
        for (int i = 1; i < NUM_TABLES; i++) {
            for (int j = 0; j < TABLE_SIZE+1; j++) {
                //uint32_t idx = getIdx(i,j);
                squareTable[i][j] = 0.f;
                sawTable[i][j] = 0.f;
                triTable[i][j] = 0.f;
            }
        }
        uint32_t numPartials, n_sq, n_tri, sqIdx, triIdx;
        float maxElementSq, maxElementSaw, maxElementTri;
        float minElementSq, minElementSaw, minElementTri;
        float normConstSq, normConstSaw, normConstTri;
        float normRatioSq, normRatioSaw, normRatioTri;
        double gibbsCoef, kGibbs;
        double amplSq, amplSaw, amplTri;
        // create tables such that the table with index 0 (used for the highest frequencies) has
        // only one harmonic (i.e. a sinewave) while for each increase of 1 in the table index the
        // number of harmonics is doubled
        for (int i = 1; i < NUM_TABLES; i++) {
            numPartials = 1 << i; // double the number of harmonics for each table
            kGibbs = (PI/(2*(float)numPartials));
            for (int k = 0; k < numPartials; k++) {
                // square wave has only odd-numbered harmonics
                n_sq = 2*k+1;
                n_tri = k+1;
                // coef to smooth out gibbs phenomena (ringing at sharp corners):(cos(k*Pi/2*numPartials))^2
                gibbsCoef = pow(cos(k*kGibbs),2.);
                // the amplitude of a given partial in the fourier series for the waveform
                amplSq = 4./(PI*n_sq);
                amplSaw = 1./n_tri;
                amplTri = (8*sin(0.5*PI*n_tri))/(pow((PI*n_tri),2.));
                // indices to add the appropriate harmonics for a given wave
                sqIdx = 0;
                triIdx = 0;
                for (int j = 0; j < TABLE_SIZE; j++) {
                    squareTable[i][j] += (float)(amplSq*gibbsCoef)*sineVec[sqIdx];
                    sawTable[i][j] += (float)(amplSaw*gibbsCoef)*sineVec[triIdx];
                    triTable[i][j] += (float)(amplTri*gibbsCoef)*sineVec[triIdx];
                    sqIdx = (sqIdx+n_sq) % TABLE_SIZE;
                    triIdx = (triIdx+n_tri) % TABLE_SIZE;
                }
            }
            squareTable[i][TABLE_SIZE] = squareTable[i][0]; //interpolation guard bit
            sawTable[i][TABLE_SIZE] = sawTable[i][0];
            triTable[i][TABLE_SIZE] = triTable[i][0];

            float* sqIter = &(((*tableRefs)[1])[i][0]);
            float* sawIter = &(((*tableRefs)[2])[i][0]);
            float* triIter = &(((*tableRefs)[3])[i][0]);
            // find normalization constants to scale waveform to between -1 and 1
            maxElementSq = *(std::max_element(sqIter,sqIter+TABLE_SIZE+1));
            minElementSq = *(std::min_element(sqIter,sqIter+TABLE_SIZE+1));
            normConstSq = (maxElementSq+minElementSq)/2.f; //subtracting this makes the average 0
            normRatioSq = 2.f/(maxElementSq-minElementSq); // multiplying by this makes the magnitude 2

            maxElementSaw = *(std::max_element(sawIter,sawIter+TABLE_SIZE+1));
            minElementSaw = *(std::min_element(sawIter,sawIter+TABLE_SIZE+1));
            normConstSaw = (maxElementSaw+minElementSaw)/2.f; //subtracting this makes the average 0
            normRatioSaw = 2.f/(maxElementSaw-minElementSaw); // multiplying by this makes the magnitude 2

            maxElementTri = *(std::max_element(triIter,triIter+TABLE_SIZE+1));
            minElementTri = *(std::min_element(triIter,triIter+TABLE_SIZE+1));
            normConstTri = (maxElementTri+minElementTri)/2.f; //subtracting this makes the average 0
            normRatioTri = 2.f/(maxElementTri-minElementTri); // multiplying by this makes the magnitude 2

            // normalize tables to between -1 and 1
            for (int j = 0; j < NUM_TABLES+1; j++) {
                //idx = getIdx(i,j);
                squareTable[i][j] = (squareTable[i][j]-normConstSq)*normRatioSq;
                sawTable[i][j] = (sawTable[i][j]-normConstSaw)*normRatioSaw;
                triTable[i][j] = (triTable[i][j]-normConstTri)*normRatioTri;
            }
        }
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_initSineVec(JNIEnv* env, jclass) {
        for(int i = 0;i<TABLE_SIZE; i++){
            sineVec[i] = (float)(sin(2*PI*((double)i/TABLE_SIZE)));
        }
        sineVec[TABLE_SIZE] = sineVec[0]; //guard bit for linear interpolation
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_MainActivity_setCarrierWaveform(JNIEnv* env, jclass, jint wave) {
        carWave = (WaveType)wave;
        //determine what type of wave to use
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_MainActivity_setModWaveform(JNIEnv* env, jclass, jint wave) {
        modWave = (WaveType)wave;
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_MainActivity_setCarrierADSR(JNIEnv *env, jclass, jfloat A, jfloat D, jfloat S, jfloat R) {
        for (int i = 0; i < NUM_VOICES; i++) {
            carrierADSR[i].setEnvelope(A,D,S,R);
            carrierADSR[i].resetTime();
        }
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_MainActivity_setModADSR(JNIEnv *env, jclass, jfloat A, jfloat D, jfloat S, jfloat R) {
        for (int i = 0; i < NUM_VOICES; i++) {
            modADSR[i].setEnvelope(A,D,S,R);
            modADSR[i].resetTime();
        }
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_MainActivity_setModIdx(JNIEnv *env, jclass, jfloat max, jfloat min) {
        for (int i = 0; i < NUM_VOICES; i++) {
            //modADSR[i].setEnvLimits(max,min);
            modADSR[i].setEnvLimits(1.f,0.f);
        }
        modIdxMax = max;
        modIdxMin = min;
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_setCarrierEnvLimits(JNIEnv *env, jclass, jfloat max, jfloat min) {
        for (int i = 0; i < NUM_VOICES; i++) {
            carrierADSR[i].setEnvLimits(max,min);
        }
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_MainActivity_setModToCarRatio(JNIEnv* env, jclass, jdouble ratio) {
        modToCarRatio = ratio;
    }


    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_noteOff(JNIEnv* env, jclass) {
        carrierADSR[oldOscIdx].sustainOff();
        modADSR[oldOscIdx].sustainOff();
    }

}
