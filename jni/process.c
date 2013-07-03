#include <jni.h>
#include "fftw3/include/fftw3.h"
#include <math.h> //for cos and log10 functions

JNIEXPORT void JNICALL Java_org_ece420_lab4_Lab4Activity_process
  (JNIEnv *env, jclass obj, jobject inbuf, jobject outbuf, jint N)
{
	int i;
	short* inBuf = (short*)(*env)->GetDirectBufferAddress(env,inbuf);
	double* outBuf = (double*)(*env)->GetDirectBufferAddress(env,outbuf);

	/***************************** Jian Chen ********************************/
	double w[N];
	double temp[N];
	for (i=0;i<N;i++)
	{
		w[i] = 0.54-0.45*cos(2*3.1415926*i/N);
	}
	for (i=0;i<N;i++)
	{
		temp[i] = ((double)inBuf[i])*w[i];
	}
	fftw_plan my_plan;
	fftw_complex *in, *out;
	in = (fftw_complex*) fftw_malloc(sizeof(fftw_complex)*2*N);
	out = (fftw_complex*) fftw_malloc(sizeof(fftw_complex)*2*N);
	my_plan = fftw_plan_dft_1d(2*N, in, out, FFTW_FORWARD, FFTW_ESTIMATE);

	for (i=0;i<N;i++)
	{
		in[i][0] = temp[i]; // change this back!!!!
		in[i][1] = 0;
	}
	for (i=N;i<(2*N);i++)
	{
		in[i][0] = 0;
		in[i][1] = 0;
	}

	fftw_execute(my_plan);

	double temp1[N];
	for (i=0;i<N;i++)
	{
		temp1[i] = log10(out[i][0]*out[i][0] + out[i][1]*out[i][1]);

		if (temp1[i]>12)
		{
			temp1[i] = 12;
		}
		else if(temp1[i]<7)
		{
			temp1[i] = 7;
		}
		outBuf[i] = (temp1[i]*0.2)-1.4; //(12.5 6.5;1/6 5/6) (1/6 -1; 12,6)

	}

	fftw_destroy_plan(my_plan);
	fftw_free(in);
	fftw_free(out);
	/***************************** Jian Chen ********************************/


}
