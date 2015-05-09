package com.example.rec;

import org.achartengine.*;
import org.achartengine.chart.*;
import org.achartengine.model.*;
import org.achartengine.renderer.*;

import android.content.*;
import android.graphics.*;

public class RecPage_LineGraph {

	private GraphicalView view;

	private TimeSeries dataset = new TimeSeries("");
	private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
	public XYSeriesRenderer renderer = new XYSeriesRenderer();
	public XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();

	public RecPage_LineGraph() {
		// Add single dataset to multiple dataset
		mDataset.addSeries(dataset);

		// 그래프의 선 스타일 설정
		renderer.setColor(Color.WHITE);
		renderer.setPointStyle(PointStyle.CIRCLE);
		renderer.setLineWidth(10);
		renderer.setFillPoints(true);

		// Enable Zoom
		mRenderer.setZoomButtonsVisible(false);
		//X,Y축 항목이름과 글자 크기
		mRenderer.setXTitle("경과시간");
		mRenderer.setYTitle("데시벨");
		mRenderer.setAxisTitleTextSize(20);
		//수치값 글자 크기와 X,Y축 최소,최대 값
		mRenderer.setLabelsTextSize(15);
		mRenderer.setYAxisMin(0);
		mRenderer.setYAxisMax(100);
		//X,Y축 스크롤 여부 ON/OFF
		mRenderer.setPanEnabled(false, false);
		//Zoom기능 On/Off
		mRenderer.setZoomEnabled(false, false);
		//그래프 배경 바꾸기
		mRenderer.setApplyBackgroundColor(true);
		mRenderer.setBackgroundColor(Color.BLACK);
		//그래프의 배경에 격자 보여주기
		mRenderer.setShowGrid(true);

		// Add single renderer to multiple renderer
		mRenderer.addSeriesRenderer(renderer);
	}

	public GraphicalView getView(Context context) {
		view = ChartFactory.getLineChartView(context, mDataset, mRenderer);
		return view;
	}

	public void addNewPoints(RecPage_Point p) {
		dataset.add(p.getX(), p.getY());
	}
}
