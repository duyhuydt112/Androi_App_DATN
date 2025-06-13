package com.p4f.objecttracking;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment dùng để hiển thị đồ thị từ mảng dữ liệu float[]
 */
public class GraphFragment extends Fragment {

    private static final String ARG_GRAPH_DATA = "param_graph_data";
    private float[] graphData;

    public GraphFragment() {
        // Required empty public constructor
    }

    public static GraphFragment newInstance(float[] data) {
        GraphFragment fragment = new GraphFragment();
        Bundle args = new Bundle();
        args.putFloatArray(ARG_GRAPH_DATA, data);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            graphData = getArguments().getFloatArray(ARG_GRAPH_DATA);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        LineChart lineChart = view.findViewById(R.id.lineChart);

        if (graphData != null && graphData.length > 0) {
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < graphData.length; i++) {
                entries.add(new Entry(i, graphData[i]));
            }

            LineDataSet dataSet = new LineDataSet(entries, "Dữ liệu đồ thị");
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(3f);
            dataSet.setDrawValues(false);

            lineChart.setData(new LineData(dataSet));
            lineChart.invalidate(); // vẽ lại
        }

        return view;
    }
}
