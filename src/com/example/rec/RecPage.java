package com.example.rec;

import org.achartengine.*;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;

public class RecPage extends Activity {
	LinearLayout layout, graphLayout;
	//녹음을 위한 변수
	final int High = 44100;
	final int Middle = 11025;
	final int Low = 8000;
	RecordAudio recordTask;
	int rate = Low;
	int inchannelConfig = AudioFormat.CHANNEL_IN_MONO;
	int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	RecPage_AudioReader AudioReader;
	int sempledecimate = 1;
	//decibel을 계산하기 위한 변수
	int sampleDecimate = 1;
	//계산된 decibel을 받아오기 위한 변수
	static int realdB;
	//실시간 그래프를 그리기 위한 변수
	private static GraphicalView view;
	private RecPage_LineGraph line = new RecPage_LineGraph();
	int blockSize;
	//녹음된 Hz의 최대값을 가지고 있기 위한 변수
	int max;
	//재생,정지버튼
	Button mStartBtn, mPlayBtn;
	//녹음상태를 나타내기위한 변수 true=녹음중, false=녹음중아님
	boolean isRecording;
	//재생 눌렀을때 재생될 위치를 받아볼 변수
	String recordingFile;
	//녹음시간을 나타내기 위한 변수
	Chronometer cm;
	//decibel를 나타내기위한 텍스트변수
	TextView decibel;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//액티비티 상단의 제목표시줄(TitleBar)를 없애줌
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.recui);
		layout = (LinearLayout)findViewById(R.id.RecUI);
		graphLayout = (LinearLayout)findViewById(R.id.graphLayout);
		layout.setBackgroundResource(R.drawable.backimg);
		mStartBtn = (Button)findViewById(R.id.recorded);
		mPlayBtn = (Button)findViewById(R.id.play);
		cm = (Chronometer)findViewById(R.id.chronometer1);
		decibel = (TextView)findViewById(R.id.decibel);
		mPlayBtn.setEnabled(false);
		isRecording = false;
		realdB = 0;
		
		AudioReader = new RecPage_AudioReader();

		//blockSize 설정
		blockSize = 256;
		
		mStartBtn.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				if (isRecording == false) {
					AudioReader.startReader(rate, blockSize*sempledecimate, new RecPage_AudioReader.Listener() {
						public void onReadError(int error) { }
						public void onReadComplete(int decibel) { realdB = decibel+100; }
					});
					recordTask = new RecordAudio();
					recordTask.execute();
					cm.setBase(SystemClock.elapsedRealtime());
					cm.start();
					isRecording = true;
					mPlayBtn.setEnabled(false);
					mStartBtn.setText("녹음중지");
				} else {
					recordTask.cancel(true);
					cm.setBase(SystemClock.elapsedRealtime());
					cm.stop();
					isRecording = false;
					mPlayBtn.setEnabled(true);
					mStartBtn.setText("녹음시작");
				}
			}
		});

		mPlayBtn.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View V) {
				mPlayBtn.setEnabled(false);
				recordingFile = AudioReader.getRecordingFile();
				Intent PlayActivity = new Intent(RecPage.this, MediaPlay.class);
				PlayActivity.putExtra("Path", recordingFile);
				startActivity(PlayActivity);
				Toast.makeText(RecPage.this, "방금 녹음한 파일이 재생됩니다.", Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	private class RecordAudio extends AsyncTask<Void, Integer, Void> {
		int i;
		protected Void doInBackground(Void... params) {
			while (isRecording) {
				try {
					line.mRenderer.setXAxisMin(i-15);
					line.mRenderer.setXAxisMax(i + 1);
					publishProgress(i++);
					Thread.sleep(100);
				} catch (InterruptedException e) { e.printStackTrace(); }
			}//end while
			return null;
		}//end doInBackground

		protected void onProgressUpdate(Integer... params) {
			RecPage_Point p = new RecPage_Point(params[0], realdB); // We got new data
			line.addNewPoints(p);// Add it to our graph
			view.repaint();
			decibel.setText(String.valueOf(realdB));
		}//end onProgressUpdate
	}
	
	protected void onStart(){
		super.onStart();
		view = line.getView(this);
		graphLayout.addView(view);
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.rec, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
