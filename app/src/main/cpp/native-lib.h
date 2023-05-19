//
// Created by augh on 4/20/21.
//

#ifndef TABLETFM_INLINE_PHASEOSC_NATIVE_LIB_H
#define TABLETFM_INLINE_PHASEOSC_NATIVE_LIB_H

float getFMOutput(void);
void updatePhase(void);
void setWavetable(uint32_t len, int32_t* table, double topFreq);
void clearRegs();


#endif //TABLETFM_INLINE_PHASEOSC_NATIVE_LIB_H
