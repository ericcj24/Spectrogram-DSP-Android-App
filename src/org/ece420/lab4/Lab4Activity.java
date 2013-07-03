/******************************************************************************************/
/*                                                                                        */
/* FILENAME                                                                               */
/*    Lab4Activity.java                                                                   */
/*                                                                                        */
/* DESCRIPTION                                                                            */
/*     Source code for implementing a real-time spectrogram viewer in hybrid Java/C code. */
/*     This file provides the Lab4Activity class definition and is responsible for        */
/*     reading and writing the audio buffer, calling the C processing code, and updating  */
/*     the screen. This code is designed to be used with Lab 4 in ECE 420 at the          */
/*     University of Illinois:                                                            */
/*                         http://cnx.org/content/m45767/latest/                          */
/*                                                                                        */ 
/* REVISION                                                                               */
/*     Revision: 1.0                                                                      */
/*     Author  : David Jun                                                                */
/*----------------------------------------------------------------------------------------*/
/*                                                                                        */
/* HISTORY                                                                                */
/*     Revision: 1.0, created February 12, 2013                                           */
/*                                                                                        */
/******************************************************************************************/

package org.ece420.lab4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.ShortBuffer;
import org.ece420.lab4.R;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class Lab4Activity extends Activity {
	int frequency = 8000;
	int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
	int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	
	int 	blockSize = 256;					// block size in samples
	int		blockSizeInBytes = 2*blockSize;		// block size in bytes
	boolean on = false;							// process data as long as this is true
	
	RecordAudio recordTask;
	
	ImageView	imageView;
	Bitmap 		bitmap;
	Canvas 		canvas;
	Paint 		paint;
	LinearLayout layout;
	
	private static final boolean FILE_INPUT = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
	private class RecordAudio extends AsyncTask<Void, DoubleBuffer, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
			AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
					frequency,
					channelConfiguration,
					audioEncoding,
					bufferSize);
			
			ByteBuffer buffer = ByteBuffer.allocateDirect(blockSize*(Short.SIZE/Byte.SIZE));
			DoubleBuffer processed = ByteBuffer.allocateDirect(blockSize*(Double.SIZE/Byte.SIZE))
					.order(ByteOrder.nativeOrder()).asDoubleBuffer();

			try{
				audioRecord.startRecording();
			}
			catch(IllegalStateException e) {
				Log.e("Recording failed", e.toString());
			}
    		
    		while(on) {
    			// read block of MIC data
    			int bufferReadResult = audioRecord.read(buffer, blockSizeInBytes);

    			// convert buffer to short so we don't have to deal with Endianness in C
    			ShortBuffer sb = ((ByteBuffer)buffer.rewind()).order(ByteOrder.nativeOrder()).asShortBuffer();
 
    			// debugging: use a test vector
    			if(FILE_INPUT) {
    				for(int i=0;i<blockSize;i++) {
    					sb.put(LOOKUP.TESTVEC[i%LOOKUP.N]);
    				}
    				bufferReadResult = blockSizeInBytes;
    			}

    			/************ NATIVE DATA/SIGNAL PROCESSING TASK *************/
    			process(sb, processed, bufferReadResult/Short.SIZE*Byte.SIZE);
    			
    			// update screen with processed results
    			publishProgress(processed);
    		}
    		try{
    			audioRecord.stop();
    		}
    		catch(IllegalStateException e) {
    			Log.e("Stop failed", e.toString());
    		}
    		    		
    		return null;
    	}
    	
    	protected void onProgressUpdate(DoubleBuffer... newDisplayUpdate) {
    		// emulate a scrolling window
    		Rect srcRect = new Rect(0, -(-1), bitmap.getWidth(), bitmap.getHeight());
    		Rect destRect = new Rect(srcRect);
    		destRect.offset(0, -1);
    		canvas.drawBitmap(bitmap, srcRect, destRect, null);
    		
    		// display new data in right-most column
    		for(int i=0;i<newDisplayUpdate[0].capacity();i++) {
    			// map value, which is between 0.0 and 1.0, to an RGB color
    			int[] rgb = colorMap(newDisplayUpdate[0].get());

    			// set color with constant alpha
    	    	paint.setColor(Color.argb(255, rgb[0], rgb[1], rgb[2]));
    	    	
    	    	// paint right-most column with frequency corresponding to row i
      	    	canvas.drawRect(i, 382, i+1, 383, paint);
    		}
    		newDisplayUpdate[0].rewind();
    		imageView.invalidate();
    	}    	
    }
	
    public void onStop() {
    	super.onStop();
    	on = false;
    	recordTask.cancel(true);
    }

    public void onStart() {
    	super.onStart();
    	setContentView(R.layout.main);
    	
    	imageView = (ImageView)this.findViewById(R.id.imageView1);
    	bitmap = Bitmap.createBitmap((int)256,(int)384,Bitmap.Config.ARGB_8888);
    	canvas = new Canvas(bitmap);
		canvas.drawColor(Color.BLACK);
    	paint = new Paint();
    	paint.setColor(Color.GREEN);
    	paint.setStyle(Paint.Style.FILL);
    	imageView.setImageBitmap(bitmap);

    	
    	on = true;
    	recordTask = new RecordAudio();
    	recordTask.execute();
    	// magic thing !!!!!!
    	
    	layout = (LinearLayout) findViewById(R.id.mylayout);
    	layout.setOnTouchListener(new OnTouchListener() {
    		public boolean onTouch(View v, MotionEvent event) {
    			if(on) {
    				on = false;
    				recordTask.cancel(true);
    			} else {
    				on = true;
    				recordTask = new RecordAudio();
    				recordTask.execute();
    			}
    			return false;
    		}
    	});
    }
    
    public int[] colorMap(double value) {
    	// implements a simple linear RYGCB colormap
        if(value <= 0.25) {
            return new int[]{0, (int)(4*value*255), (int)255};
        } else if(value <= 0.5) {
        	return new int[]{0, (int)255, (int)((1-4*(value-0.25))*255)};
        } else if(value <= 0.75) {
            return new int[]{(int)(4*(value-0.5)*255), (int)255, 0};
        } else {
            return new int[]{(int)255, (int)((1-4*(value-0.75))*255), 0};
        }
    }
    
    static {
        System.loadLibrary("process");
    }
        
    public static native void process(ShortBuffer inbuf, DoubleBuffer outbuf, int N);
}