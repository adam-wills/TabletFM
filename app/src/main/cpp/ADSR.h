//
// Created by augh on 4/18/21.
//

#ifndef TABLETFM_SCRATCH_ADSR_H
#define TABLETFM_SCRATCH_ADSR_H


class ADSR {
public:
    enum Mode {ATTACK = 0, DECAY = 1, SUSTAIN = 2, RELEASE = 3};
    ADSR();
    void setEnvelope(float a, float d, float s, float r);
    void setSampleRate(double SR);
    void sustainOff();
    void resetTime();
    float getValue();
    void incrementTime();
    void setEnvLimits(float envMax, float envMin);

    Mode getMode();

private:
    Mode mode;
    float envelope,
          attack_increment,
          decay_factor,
          sustain_level,
          release_factor;
    double sampleRate = 48000.0;
    float envMax_;
    float envMin_;
    float envAmp_;

};


#endif //TABLETFM_SCRATCH_ADSR_H
