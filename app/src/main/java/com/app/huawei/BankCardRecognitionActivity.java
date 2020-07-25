/**
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.app.huawei;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.huawei.hms.mlplugin.card.bcr.MLBcrCapture;
import com.huawei.hms.mlplugin.card.bcr.MLBcrCaptureConfig;
import com.huawei.hms.mlplugin.card.bcr.MLBcrCaptureFactory;
import com.huawei.hms.mlplugin.card.bcr.MLBcrCaptureResult;

public class BankCardRecognitionActivity extends AppCompatActivity implements View.OnClickListener {
    private ImageView bankCardFrontImg;
    private ImageView bankCardFrontSimpleImg;
    private ImageView bankCardFrontDeleteImg;
    private LinearLayout bankCardFrontAddView;
    private TextView showResult;
    private String lastFrontResult = "";
    private String lastBackResult = "";
    private Bitmap currentImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_card_recognition);

        initComponent();
    }

    private void initComponent() {
        bankCardFrontImg = findViewById(R.id.avatar_img);
        bankCardFrontSimpleImg = findViewById(R.id.avatar_sample_img);
        bankCardFrontDeleteImg = findViewById(R.id.avatar_delete);
        bankCardFrontAddView = findViewById(R.id.avatar_add);
        showResult = findViewById(R.id.show_result);

        bankCardFrontAddView.setOnClickListener(this);
        bankCardFrontDeleteImg.setOnClickListener(this);
        findViewById(R.id.back).setOnClickListener(this);
    }

    //3. 在检测按钮的回调中，调用步骤2中定义的方法，实现银行卡识别。
    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.avatar_add: //检测按钮。
                startCaptureActivity();
                break;
            case R.id.avatar_delete:
                showFrontDeleteImage();
                lastFrontResult = "";
                break;
            case R.id.back:
                finish();
                break;
            default:
                break;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentImage != null && !currentImage.isRecycled()) {
            currentImage.recycle();
            currentImage = null;
        }
    }
//2. 设置识别参数，调用识别器captureFrame接口进行识别，识别结果会通过步骤1的回调函数返回。
    private void startCaptureActivity() {
        MLBcrCaptureConfig config = new MLBcrCaptureConfig.Factory()
                // 设置银行卡识别期望返回的结果类型。
                // MLBcrCaptureConfig.SIMPLE_RESULT：仅识别卡号、有效期信息。
                // MLBcrCaptureConfig.ALL_RESULT：识别卡号、有效期、发卡行、发卡组织和卡类别等信息。
                .setOrientation(MLBcrCaptureConfig.ORIENTATION_AUTO)
                // 设置识别界面横竖屏，支持三种模式：
                // MLBcrCaptureConfig.ORIENTATION_AUTO: 自适应模式，由物理感应器决定显示方向。
                // MLBcrCaptureConfig.ORIENTATION_LANDSCAPE: 横屏模式。
                // MLBcrCaptureConfig.ORIENTATION_PORTRAIT: 竖屏模式。
                .setResultType(MLBcrCaptureConfig.RESULT_ALL)
                .create();
        MLBcrCapture bcrCapture = MLBcrCaptureFactory.getInstance().getBcrCapture(config);

        bcrCapture.captureFrame(this, this.callback);
    }

    private String formatCardResult(MLBcrCaptureResult result) {
        StringBuilder resultBuilder = new StringBuilder();

        resultBuilder.append("Number：");
        resultBuilder.append(result.getNumber());
        resultBuilder.append(System.lineSeparator());

        resultBuilder.append("Issuer：");
        resultBuilder.append(result.getIssuer());
        resultBuilder.append(System.lineSeparator());

        resultBuilder.append("Expire: ");
        resultBuilder.append(result.getExpire());
        resultBuilder.append(System.lineSeparator());

        resultBuilder.append("Type: ");
        resultBuilder.append(result.getType());
        resultBuilder.append(System.lineSeparator());

        resultBuilder.append("Organization: ");
        resultBuilder.append(result.getOrganization());
        resultBuilder.append(System.lineSeparator());

        return resultBuilder.toString();
    }

    private MLBcrCapture.Callback callback = new MLBcrCapture.Callback() {  //MLBcrCaptureResult 为识别返回结果
        @Override
        public void onSuccess(MLBcrCaptureResult result) {              // 识别成功处理。

            if (result == null) {
                return;
            }
            Bitmap bitmap = result.getOriginalBitmap();
            showSuccessResult(bitmap, result);
        }

        // 1. 创建识别结果回调函数，重载onSuccess， onCanceled， onFailure， onDenied四个方法。
        @Override
        public void onCanceled() {
            showResult.setText(" RecCanceled ");
        } // 用户取消处理。

        // 识别不到任何文字信息或识别过程发生系统异常的回调方法。
        // retCode：错误码。
        // bitmap：检测失败的卡证图片。
        @Override
        public void onFailure(int retCode, Bitmap bitmap) {

            showResult.setText(" RecFailed ");                                      // 识别异常处理。
        }

        @Override
        public void onDenied() {
            showResult.setText(" RecDenied ");
        }  // 相机不支持等场景处理。
    };

    private void showSuccessResult(Bitmap bitmap, MLBcrCaptureResult idCardResult) {
        showFrontImage(bitmap);
        lastFrontResult = formatCardResult(idCardResult);
        showResult.setText(lastFrontResult);
        showResult.append(lastBackResult);
        ((ImageView) findViewById(R.id.number)).setImageBitmap(idCardResult.getNumberBitmap());
    }

    private void showFrontImage(Bitmap bitmap) {
        bankCardFrontImg.setVisibility(View.VISIBLE);
        bankCardFrontImg.setImageBitmap(bitmap);
        bankCardFrontSimpleImg.setVisibility(View.GONE);
        bankCardFrontAddView.setVisibility(View.GONE);
        bankCardFrontDeleteImg.setVisibility(View.VISIBLE);
    }

    private void showFrontDeleteImage() {
        bankCardFrontImg.setVisibility(View.GONE);
        bankCardFrontSimpleImg.setVisibility(View.VISIBLE);
        bankCardFrontAddView.setVisibility(View.VISIBLE);
        bankCardFrontDeleteImg.setVisibility(View.GONE);
    }
}
