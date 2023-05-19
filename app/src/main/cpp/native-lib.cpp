//
// Created by augh on 4/20/21.
//

#include <jni.h>
#include <android/input.h>
#include <cmath>
#include "native-lib.h"
#include <cstdint>
#include <map>
#include <iterator>
#include <vector>



int octaveNum = 2;
std::vector<int> keyboardLayout{0,  2,  1,  4,  3,  5,
                                7,  6,  9,  8,  11, 10,
                                12, 14, 13, 16, 15, 17,
                                19, 18, 21, 20, 23, 22};

std::map<int,double> midiToFreqMap;



extern "C" {
    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_midiToFrequencyInit(JNIEnv* env, jclass) {
        for (int i = 24; i < 120; i++) {
            double f = 440.0*pow(2.,((double)(i-69)/12.));
            midiToFreqMap.insert(std::pair<int,double>(i,f));
        }
    }

    JNIEXPORT void JNICALL
    Java_com_ece420_lab5_Piano_chooseOctave(JNIEnv* env, jclass, jint octave) {
        octaveNum = octave;
    }

    JNIEXPORT double JNICALL
    Java_com_ece420_lab5_Piano_getKeyboardFreq(JNIEnv* env, jclass, jint idx){

            int noteNum = keyboardLayout.operator[](idx)+12*(octaveNum+1);
            return midiToFreqMap.at(noteNum);
    }

}
