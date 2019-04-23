package com.lk.openstreamer.ui.fragments;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.lk.openstreamer.R;
import com.lk.openstreamer.databinding.FragmentMainBinding;

@SuppressWarnings("unused")
public class FragmentMain extends BaseFragment {

    @SuppressWarnings("FieldCanBeLocal")
    private FragmentMainBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false);
        return b.getRoot();
    }
}
