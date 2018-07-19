package com.base.utils.rx;

import android.support.annotation.NonNull;

import com.base.presenter.RxLifeCyclePresenter;

import rx.Observable;

/**
 * Created by feary on 18-5-25.
 */

public interface RxLife {

    @NonNull
    <T> Observable.Transformer<T, T> bindUntilEvent();
}
