package com.lcn.rxharmony.sample.slice;

import com.lcn.rxharmony.sample.ResourceTable;
import com.lcn.rxharmony.schedulers.HarmonySchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Text;
import ohos.hiviewdfx.HiLog;

import java.util.concurrent.TimeUnit;

public class MainAbilitySlice extends AbilitySlice {

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);

        Text text = (Text) findComponentById(ResourceTable.Id_text_helloworld);

        text.setClickedListener(c -> Observable.intervalRange(0L, 10L, 0L, 1L, TimeUnit.SECONDS, Schedulers.io())
                .map(i -> "Send: " + i)
                .observeOn(HarmonySchedulers.mainThread())
                .subscribe(
                        text::setText,
                        t -> text.setText(HiLog.getStackTrace(t)),
                        () -> {}
                )
        );
    }

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }
}
