package com.example.rec;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

/**
 * A class which reads audio input from the mic in a background thread and
 * passes it to the caller when ready.
 * <p>
 * To use this class, your application must have permission RECORD_AUDIO.
 */
public class RecPage_AudioReader {
	// ******************************************************************** //
	// Public Classes.
	// ******************************************************************** //

	/**
	 * Listener for audio reads.
	 */
	public static abstract class Listener {
		/**
		 * Audio read error code: no error.
		 */
		public static final int ERR_OK = 0;

		/**
		 * Audio read error code: the audio reader failed to initialise.
		 */
		public static final int ERR_INIT_FAILED = 1;

		/**
		 * Audio read error code: an audio read failed.
		 */
		public static final int ERR_READ_FAILED = 2;

		/**
		 * An audio read has completed.
		 * 
		 * @param buffer
		 *            Buffer containing the data.
		 */
		public abstract void onReadComplete(int decibel);

		/**
		 * An error has occurred. The reader has been terminated.
		 * 
		 * @param error
		 *            ERR_XXX code describing the error.
		 */
		public abstract void onReadError(int error);
	}

	public int calculatePowerDb(short[] sdata, int off, int samples) {
		double sum = 0;
		double sqsum = 0;
		for (int i = 0; i < samples; i++) {
			final long v = sdata[off + i];
			sum += v;
			sqsum += v * v;
		}

		// sqsum is the sum of all (signal+bias)², so
		// sqsum = sum(signal²) + samples * bias²
		// hence
		// sum(signal²) = sqsum - samples * bias²
		// Bias is simply the average value, i.e.
		// bias = sum / samples
		// Since power = sum(signal²) / samples, we have
		// power = (sqsum - samples * sum² / samples²) / samples
		// so
		// power = (sqsum - sum² / samples) / samples
		double power = (sqsum - sum * sum / samples) / samples;

		// Scale to the range 0 - 1.
		power /= MAX_16_BIT * MAX_16_BIT;

		// Convert to dB, with 0 being max power. Add a fudge factor to make
		// a "real" fully saturated input come to 0 dB.
		double result = Math.log10(power) * 10f + FUDGE;
		return (int) result;
	}

	// ******************************************************************** //
	// Run Control.
	// ******************************************************************** //
	/**
	 * Start this reader.
	 * 
	 * @param rate
	 *            The audio sampling rate, in samples / sec.
	 * @param block
	 *            Number of samples of input to read at a time. This is
	 *            different from the system audio buffer size.
	 * @param listener
	 *            Listener to be notified on each completed read.
	 */
	public void startReader(int rate, int block, Listener listener) {
		//SD카드의 경로를 얻어와 저장할 폴더와 파일명을 지정
		sdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
		Date date = new Date();
		date1 = date.toString();
		dateSplit = date1.split(" ");
		date1 = dateSplit[3];
		File Path = new File(sdPath+"/Android/data/com.example.rec");
		if( !Path.exists()) Path.mkdirs();
		recordingFile = Path + "/" + date1 + ".pcm";
		try{ dos = new DataOutputStream( new BufferedOutputStream(new FileOutputStream(recordingFile))); }
		catch(Throwable t) {}
		
		synchronized (this) {
			// Calculate the required I/O buffer size.
			audioBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;
			//저장을 하기 위한 버퍼 사이즈
			bufferSize = audioBuf / 2;
			// Set up the audio input.
			audioInput = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioBuf);
			inputBlockSize = block;
			inputBuffer = new short[2][inputBlockSize];
			inputBufferWhich = 0;
			inputBufferIndex = 0;
			inputListener = listener;
			running = true;
			//저장을 하기 위한 버퍼
			savebuffer = new short[bufferSize];
			readerThread = new Thread(new Runnable() {
				public void run() { readerRun(); }
			}, "Audio Reader");
			readerThread.start();
		}
	}

	/**
	 * Stop this reader.
	 */
	public void stopReader() {
		Log.i(TAG, "Reader: Signal Stop");
		synchronized (this) {
			running = false;
			try { dos.close(); } 
			catch (IOException e) { e.printStackTrace(); }
		}
		try {
			if (readerThread != null)
				readerThread.join();
		} catch (InterruptedException e) {
			;
		}
		readerThread = null;

		// Kill the audio input.
		synchronized (this) {
			if (audioInput != null) {
				audioInput.release();
				audioInput = null;
			}
		}

		Log.i(TAG, "Reader: Thread Stopped");
	}

	// ******************************************************************** //
	// Main Loop.
	// ******************************************************************** //
	/**
	 * Main loop of the audio reader. This runs in our own thread.
	 */
	private void readerRun() {
		short[] buffer;
		int index, readSize;

		if (audioInput.getState() != AudioRecord.STATE_INITIALIZED) {
			Log.e(TAG, "Audio reader failed to initialize");
			readError(Listener.ERR_INIT_FAILED);
			running = false;
			return;
		}

		try {
			audioInput.startRecording();
			while (running) {
				if (!running) break;

				readSize = inputBlockSize;
				int space = inputBlockSize - inputBufferIndex;
				if (readSize > space) readSize = space;
				buffer = inputBuffer[inputBufferWhich];
				index = inputBufferIndex;

				synchronized (buffer) {
					int nread = audioInput.read(buffer, index, readSize);

					boolean done = false;
					if (!running) break;

					if (nread < 0) {
						Log.e(TAG, "Audio read failed: error " + nread);
						readError(Listener.ERR_READ_FAILED);
						running = false;
						break;
					}
					int end = inputBufferIndex + nread;
					if (end >= inputBlockSize) {
						inputBufferWhich = (inputBufferWhich + 1) % 2;
						inputBufferIndex = 0;
						done = true;
					} else
						inputBufferIndex = end;
					
					int bufferReadResult = audioInput.read(savebuffer, 0, 256);
					for (int i = 0; i < 256 && i < bufferReadResult; i++) {
						try { dos.writeShort(savebuffer[i]); }
						catch(Throwable t) {}
					}

					if (done) {
						readDone(buffer);
						long sleep = 100;
						try { buffer.wait(sleep); } 
						catch (InterruptedException e) { }
					}
				}
			}
		} finally {
			Log.i(TAG, "Reader: Stop Recording");
			if (audioInput.getState() == AudioRecord.RECORDSTATE_RECORDING)
				audioInput.stop();
		}
	}

	/**
	 * Notify the client that a read has completed.
	 * 
	 * @param buffer
	 *            Buffer containing the data.
	 */
	private void readDone(short[] buffer) {
		synchronized (this) {
			audioData = buffer;
			++audioSequence;

			short[] buffer2 = null;
			if (audioData != null && audioSequence > audioProcessed) {
				audioProcessed = audioSequence;
				buffer2 = audioData;
			}

			if (buffer2 != null) {
				final int len = buffer2.length;
				inputListener.onReadComplete(calculatePowerDb(buffer2, 0, len));
				buffer2.notify();
			}

		}
	}

	/**
	 * Notify the client that an error has occurred. The reader has been
	 * terminated.
	 * 
	 * @param error
	 *            ERR_XXX code describing the error.
	 */
	private void readError(int code) {
		inputListener.onReadError(code);
	}
	
	public String getRecordingFile(){
		return recordingFile;
	}

	// ******************************************************************** //
	// Class Data.
	// ******************************************************************** //

	// Debugging tag.
	private static final String TAG = "WindMeter";

	// ******************************************************************** //
	// Private Data.
	// ******************************************************************** //

	// Our audio input device.
	private AudioRecord audioInput;

	// Our audio input buffer, and the index of the next item to go in.
	private short[][] inputBuffer = null;
	private int inputBufferWhich = 0;
	private int inputBufferIndex = 0;

	// Size of the block to read each time.
	private int inputBlockSize = 0;

	// Listener for input.
	private Listener inputListener = null;

	// Flag whether the thread should be running.
	private boolean running = false;

	// The thread, if any, which is currently reading. Null if not running.
	private Thread readerThread = null;

	private short[] audioData;
	private long audioSequence = 0;
	private long audioProcessed = 0;

	private static final float MAX_16_BIT = 32768;
	private static final float FUDGE = 0.6f;
	
	//녹음파일의 저장 경로를 얻어오기 위한 변수
	String sdPath;
	String recordingFile;
	String[] dateSplit;
	String date1;
	//저장을 하기 위한 버퍼와 버퍼 사이즈
	short[] savebuffer;
	int bufferSize;
	//녹음을 위한 버퍼
	int audioBuf;
	//저장을 위한 스트림
	DataOutputStream dos;
}