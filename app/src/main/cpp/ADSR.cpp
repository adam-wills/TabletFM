//
// Created by augh on 4/18/21.
//

#include <cmath>
#include "ADSR.h"

ADSR::ADSR() :
          mode(ATTACK),
          envelope(0.f) {

}

void ADSR::setEnvelope(float a, float d, float s, float r) {
    mode = ATTACK;
    envelope = 0.f;
    attack_increment = a*sampleRate;
    sustain_level = s;

    if (attack_increment == 0) {
        envelope = envAmp_;
        mode = DECAY;
    }

    float k = (1.f/d)*(96.f/20.f)*std::logf(10.f);
    decay_factor = static_cast<float>(std::exp(-k/sampleRate));
    k = (1.f/r)*(96.f/20.f)*std::logf(10.f);
    release_factor = static_cast<float>(std::exp(-k/sampleRate));
}

void ADSR::setSampleRate(double SR) {
    sampleRate = SR;
}

void ADSR::sustainOff() {
    mode = RELEASE;
    //envelope = sustain_level*envAmp_;
    envelope *= release_factor;
}

void ADSR::resetTime() {
    //envelope = 0.f;
    mode = ATTACK;
}

float ADSR::getValue() {
    return envelope+envMin_;
}

void ADSR::incrementTime() {
    if (mode == ATTACK) {
        envelope += (envAmp_/attack_increment);
        if (envelope >= envAmp_) {
            envelope = envAmp_;
            mode = DECAY;
        }
    }
    else if (mode == DECAY) {
        envelope *= decay_factor;
        if (envelope <= sustain_level*envAmp_) {
            envelope = sustain_level*envAmp_;
            mode = SUSTAIN;
        }
    }
    else if (mode == RELEASE) {
        envelope *= release_factor;
    }
}

ADSR::Mode ADSR::getMode() {
    return mode;
}

void ADSR::setEnvLimits(float envMax, float envMin) {
    envMax_ = envMax;
    envMin_ = envMin;
    envAmp_ = envMax_-envMin_;
}
