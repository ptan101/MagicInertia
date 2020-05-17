package com.example.magicinertia;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.magicinertia.databinding.ActivitySampleGraphBinding;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Random;

public class SampleGraphActivity extends AppCompatActivity {

    private ActivitySampleGraphBinding mBinding;
    private final Handler mHandler = new Handler();
    private Runnable mTimer1;
    private LineGraphSeries<DataPoint> mSeries1;
    private LineGraphSeries<DataPoint> mSeries2;
    private double graphLastXValue = 5d;

    GraphView graph;
    GraphView graph2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_graph);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_sample_graph);

        graph = mBinding.graph;
        graph2 = mBinding.graph2;

        mSeries1 = new LineGraphSeries<>();
        graph.addSeries(mSeries1);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(1000);
        graph.getViewport().setMaxY((60.15));
        graph.getViewport().setMinY((59.85));

        mSeries2 = new LineGraphSeries<>();
        graph2.addSeries(mSeries2);
        graph2.getViewport().setXAxisBoundsManual(true);
        graph2.getViewport().setYAxisBoundsManual(true);
        graph2.getViewport().setMinX(0);
        graph2.getViewport().setMaxX(1000);
        graph2.getViewport().setMaxY((125));
        graph2.getViewport().setMinY((115));
    }

    @Override
    public void onResume() {
        super.onResume();
        mTimer1 = new Runnable() {
            @Override
            public void run() {
                graphLastXValue += 1d;
                mSeries2.appendData(new DataPoint(graphLastXValue, getRandom2()), true, 1000);
                mSeries1.appendData(new DataPoint(graphLastXValue, getRandom1()), true, 1000);
                mHandler.postDelayed(this, 20);
            }
        };
        mHandler.postDelayed(mTimer1, 20);
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacks(mTimer1);
        super.onPause();
    }

    private DataPoint[] generateData() {
        int count = 30;
        DataPoint[] values = new DataPoint[count];
        for (int i=0; i<count; i++) {
            double x = i;
            double f = mRand.nextDouble()*0.15+0.3;
            double y = Math.sin(i*f+2) + mRand.nextDouble()*0.3;
            DataPoint v = new DataPoint(x, y);
            values[i] = v;
        }
        return values;
    }

    double m1LastRandom = 60;
    Random mRand = new Random();
    private double getRandom1() {
        m1LastRandom += mRand.nextDouble()*0.01 - 0.005;
        if(m1LastRandom > 60.12)
            m1LastRandom -= 0.03;
        else if (m1LastRandom < 59.88)
            m1LastRandom += 0.03;
        return m1LastRandom;
    }

    double m2LastRandom = 120;
    private double getRandom2() {
        m2LastRandom += mRand.nextDouble()*0.5 - 0.25;
        if(m2LastRandom > 125)
            m1LastRandom -= 0.3;
        else if (m2LastRandom < 115)
            m2LastRandom += 0.3;
        return m2LastRandom;
    }
}
