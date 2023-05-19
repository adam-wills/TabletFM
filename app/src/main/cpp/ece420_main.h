//
// Created by daran on 1/12/2017 to be used in ECE420 Sp17 for the first time.
// Modified by dwang49 on 1/1/2018 to adapt to Android 7.0 and Shield Tablet updates.
//

#ifndef ECE420_MAIN_H
#define ECE420_MAIN_H

//constants
#define FRAME_SIZE          1024
#define F_S                 (double)48000.
#define FS_INV              (double)1./F_S
#define PI                  3.141592654
#define AMP_BITS            16
#define AMP                 (double)((1 << (AMP_BITS-1))-1)
#define TABLE_BITS          12
#define TABLE_SIZE          (1<<TABLE_BITS)
#define FIXED_SHIFT         24
#define DECIMAL_MASK        ((1<<FIXED_SHIFT)-1)
#define NUM_TABLES          8
#define NUM_VOICES          1

#include "audio_common.h"
#include "buf_manager.h"
#include "ADSR.h"

void FMSynthesis(int16_t* bufferOut,
                 float (*carTable)[NUM_TABLES][TABLE_SIZE+1],
                 float (*modTable)[NUM_TABLES][TABLE_SIZE+1]);
float sampleInterp(float (*table)[NUM_TABLES][TABLE_SIZE+1], uint32_t tIdx, uint32_t sIdx, double sInterp);
float tableInterp(float (*table)[NUM_TABLES][TABLE_SIZE+1], int32_t phReg, double freqReg);
void ece420ProcessFrame(sample_buf *dataBuf);


#endif //ECE420_MAIN_H
