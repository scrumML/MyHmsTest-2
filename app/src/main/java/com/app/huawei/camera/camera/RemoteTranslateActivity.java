//
package com.app.huawei.camera.camera;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.app.huawei.R;
import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.mlsdk.MLAnalyzerFactory;
import com.huawei.hms.mlsdk.common.MLFrame;
import com.huawei.hms.mlsdk.common.internal.client.SmartLog;
import com.huawei.hms.mlsdk.text.MLRemoteTextSetting;
import com.huawei.hms.mlsdk.text.MLText;
import com.huawei.hms.mlsdk.text.MLTextAnalyzer;
import com.huawei.hms.mlsdk.translate.MLTranslatorFactory;
import com.huawei.hms.mlsdk.translate.cloud.MLRemoteTranslateSetting;
import com.huawei.hms.mlsdk.translate.cloud.MLRemoteTranslator;
import com.app.huawei.util.BitmapUtils;
import com.app.huawei.util.Constant;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RemoteTranslateActivity extends AppCompatActivity {
    private static String TAG = "RemoteTranslateActivity";
    private RelativeLayout relativeLayoutLoadPhoto, relativeLayoutTakePhoto, relativeLayoutTranslate;
    private ImageView preview; //
    private TextView textView;
    private Uri imageUri;
    private String path;
    private Bitmap originBitmap; // 本地读取/拍照 获得的 即将被识别的 图片
    private Integer maxWidthOfImage;
    private Integer maxHeightOfImage;
    boolean isLandScape;
    private int REQUEST_CHOOSE_ORIGINPIC = 2001;
    private int REQUEST_TAKE_PHOTO = 2000;
    private static final String KEY_IMAGE_URI = "KEY_IMAGE_URI";
    private static final String KEY_IMAGE_MAX_WIDTH =
            "KEY_IMAGE_MAX_WIDTH";
    private static final String KEY_IMAGE_MAX_HEIGHT =
            "KEY_IMAGE_MAX_HEIGHT";
    private String sourceText = ""; // 通过图片识别 得到的 文字！

    private String srcLanguage = "Auto";
    private String dstLanguage = "EN";

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        this.setContentView(R.layout.activity_translate_photo); // .xml
        Intent intent = this.getIntent();
        try {
            this.srcLanguage = intent.getStringExtra(Constant.SOURCE_VALUE);
            this.dstLanguage = intent.getStringExtra(Constant.DEST_VALUE);
        } catch (RuntimeException e) {
            SmartLog.e(RemoteTranslateActivity.TAG, "Get intent value failed:" + e.getMessage());
        }
        this.findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RemoteTranslateActivity.this.finish();
            }
        });
        this.isLandScape =
                (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        this.initView();     //todo  初始化页面
        this.initAction();  // 触发3个功能按钮
    }

    private void initView() {
        this.relativeLayoutLoadPhoto = this.findViewById(R.id.relativate_chooseImg); //选择图片
        this.relativeLayoutTakePhoto = this.findViewById(R.id.relativate_camera); //拍照-按钮
        this.relativeLayoutTranslate = this.findViewById(R.id.relativate_translate); //翻译-按钮
        this.preview = this.findViewById(R.id.previewPane);                 // preview 窗
        this.textView = this.findViewById(R.id.translate_result);           // 翻译结果
    }

    private void initAction() {
        this.relativeLayoutLoadPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {                                               //todo 选择 加载本地图片 进行翻译
                RemoteTranslateActivity.this.selectLocalImage(RemoteTranslateActivity.this.REQUEST_CHOOSE_ORIGINPIC);
            }
        });
        // Outline the edge.
        this.relativeLayoutTranslate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {            //todo 点击 翻译按钮 进行 翻译
                if (RemoteTranslateActivity.this.imageUri == null
                        && RemoteTranslateActivity.this.path == null) {
                    Toast.makeText(RemoteTranslateActivity.this.getApplicationContext(), R.string.please_select_picture, Toast.LENGTH_SHORT).show();
                } else {
                    RemoteTranslateActivity.this.createRemoteTextAnalyzer();
                    Toast.makeText(RemoteTranslateActivity.this.getApplicationContext(), R.string.translate_start, Toast.LENGTH_SHORT).show();
                }
            }
        });
        this.relativeLayoutTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {         //todo 选择 拍照 再翻译
                RemoteTranslateActivity.this.takePhoto(RemoteTranslateActivity.this.REQUEST_TAKE_PHOTO);
            }
        });
    }

    //todo 拍照片
    private void takePhoto(int requestCode) {
        Intent intent = new Intent(RemoteTranslateActivity.this, CapturePhotoActivity.class);
        this.startActivityForResult(intent, requestCode);
    }
    //todo 选择本地照片
    private void selectLocalImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        this.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //todo 获取 本地选择的图片 imageUri
        if ((requestCode == this.REQUEST_CHOOSE_ORIGINPIC) //选择 相册导入
                && (resultCode == Activity.RESULT_OK)) {
            // In this case, imageUri is returned by the chooser, save it.
            this.imageUri = data.getData();
            this.loadOriginImage(); // 调用HMS api，获取并存储图片 在 originBitmap
        } else if ((requestCode == this.REQUEST_TAKE_PHOTO) //选择 拍照 //todo 获取相机拍照的图片
                && (resultCode == Activity.RESULT_OK)
                && data != null) {
            this.path = data.getStringExtra(Constant.IMAGE_PATH_VALUE);
            this.loadCameraImage(); // 调用HMS api， 获取并存储图片 在 originBitmap
        }
    }

    //todo 获取 相机拍照 的图片， originBitmap
    private void loadCameraImage() {
        if (this.path == null) {
            return;
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(path);
            this.originBitmap = BitmapFactory.decodeStream(fis);
            this.originBitmap = this.originBitmap.copy(Bitmap.Config.ARGB_4444, true);
            this.preview.setImageBitmap(this.originBitmap); //获取并存储图片 在 originBitmap
        } catch (IOException error) {
            error.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException error) {
                    error.printStackTrace();
                }
            }
        }
    }


    private MLTextAnalyzer textAnalyzer;
    private MLRemoteTranslator translator;

    //1. 创建文本翻译器。可以通过文本翻译器自定义参数类MLLocalTranslateSetting创建翻译器。
    private void createRemoteTranslator() {
        //// 创建离线翻译器。
        MLRemoteTranslateSetting.Factory factory = new MLRemoteTranslateSetting // 通过 文本翻译器 自定义参数类“MLRemoteTranslateSetting”创建翻译器。
                .Factory()
                // Set the target language code. The ISO 639-1 standard is used. // 设置目标语言的编码
                .setTargetLangCode(this.dstLanguage);
        if (!this.srcLanguage.equals("AUTO")) {
            // Set the source language code. The ISO 639-1 standard is used.
            factory.setSourceLangCode(this.srcLanguage); // 设置源语言的编码
        }
        this.translator = MLTranslatorFactory.getInstance().getRemoteTranslator(factory.create()); //creat
        //todo 2. 进行文本翻译。
        final Task<String> task = translator.asyncTranslate(this.sourceText); // 调用“asyncAnalyseFrame ”方法对文本识别获取的内容进行文本翻译
        task.addOnSuccessListener(new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String text) {
                if (text != null) {
                    RemoteTranslateActivity.this.remoteDisplaySuccess(text); //todo 翻译成功
                } else {
                    RemoteTranslateActivity.this.displayFailure();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                RemoteTranslateActivity.this.displayFailure();
            }
        });
    }

    // 1. 创建云侧文本分析器。可以通过文本检测配置器“MLRemoteTextSetting”创建文本分析器。
    private void createRemoteTextAnalyzer() {
        MLRemoteTextSetting setting = (new MLRemoteTextSetting.Factory()). //todo 进行识别
                // 设置云侧文本检测模式：
                // MLRemoteTextSetting.OCR_COMPACT_SCENE：文本密集场景的文本识别。
                // MLRemoteTextSetting.OCR_LOOSE_SCENE：文本稀疏场景的文本识别。
                setTextDensityScene(MLRemoteTextSetting.OCR_LOOSE_SCENE).create();
        // 或者方式二：使用默认参数配置，自动检测语种进行识别，适用于文本稀疏场景，文本框返回格式为：MLRemoteTextSetting.NGON。
        //MLTextAnalyzer analyzer = MLAnalyzerFactory.getInstance().getRemoteTextAnalyzer();
        this.textAnalyzer = MLAnalyzerFactory.getInstance().getRemoteTextAnalyzer(setting);
   //      2. 通过Bitmap创建MLFrame，支持的图片格式包括：jpg/jpeg/png/bmp。
        if (this.isChosen(this.originBitmap)) {
            MLFrame mlFrame = new MLFrame.Creator().setBitmap(this.originBitmap).create(); //通过android.graphics.Bitmap创建“MLFrame”对象用于分析器检测图片
   //      3. 将创建的MLFrame对象传递给分析器的“asyncAnalyseFrame”方法进行文字识别
            Task<MLText> task = this.textAnalyzer.asyncAnalyseFrame(mlFrame); //调用“asyncAnalyseFrame ”方法进行文本检测
            task.addOnSuccessListener(new OnSuccessListener<MLText>() {
                @Override
                public void onSuccess(MLText mlText) {
                    // Transacting logic for segment success. //todo 识别成功
                    if (mlText != null) {
                        RemoteTranslateActivity.this.remoteDetectSuccess(mlText);
                    } else {
                        RemoteTranslateActivity.this.displayFailure(); //识别 失败
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    // Transacting logic for segment failure.
                    RemoteTranslateActivity.this.displayFailure(); // 识别 失败
                    return;
                }
            });
        } else {
            Toast.makeText(this.getApplicationContext(), R.string.please_select_picture, Toast.LENGTH_SHORT).show();
            return;
        }
    }

    //todo 图片中文字 的 识别结果 sourceText
    private void remoteDetectSuccess(MLText mlTexts) {
        this.sourceText = "";
        List<MLText.Block> blocks = mlTexts.getBlocks();
        List<MLText.TextLine> lines = new ArrayList<>();
        for (MLText.Block block : blocks) {
            for (MLText.TextLine line : block.getContents()) {
                if (line.getStringValue() != null) {
                    lines.add(line); // 文字行叠加
                }
            }
        }
        Collections.sort(lines, new Comparator<MLText.TextLine>() {
            @Override
            public int compare(MLText.TextLine o1, MLText.TextLine o2) {
                Point[] point1 = o1.getVertexes();
                Point[] point2 = o2.getVertexes();
                return point1[0].y - point2[0].y;
            }
        });
        for (int i = 0; i < lines.size(); i++) {
            this.sourceText = this.sourceText + lines.get(i).getStringValue().trim() + "\n"; //todo 得到识别的文字 sourceText
        }
        this.createRemoteTranslator();//todo 得到识别的结果进行翻译
    }

    //todo 翻译成功 提示
    private void remoteDisplaySuccess(String test) {
        String[] sourceLines = sourceText.split("\n");
        String[] drtLines = test.split("\n");
        for (int i = 0; i < sourceLines.length && i < drtLines.length; i++) {
            this.textView.append(sourceLines[i] + "-> " + drtLines[i] + "\n");
        }
        Toast.makeText(RemoteTranslateActivity.this.getApplicationContext(), R.string.translate_success, Toast.LENGTH_SHORT).show();
    }
    //todo 翻译失败 提示
    private void displayFailure() {
        Toast.makeText(this.getApplicationContext(), "Fail", Toast.LENGTH_SHORT).show();
    }
    private boolean isChosen(Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        } else {
            return true;
        }
    }
    //todo 获取选择的图片 originBitmap（resized）
    private void loadOriginImage() {
        if (this.imageUri == null) {
            return;
        }
        Pair<Integer, Integer> targetedSize = this.getTargetSize();
        int targetWidth = targetedSize.first;
        int maxHeightOfImage = targetedSize.second;
        this.originBitmap = BitmapUtils.loadFromPath(RemoteTranslateActivity.this, this.imageUri, targetWidth, maxHeightOfImage);
        // Determine how much to scale down the image.
        SmartLog.i(RemoteTranslateActivity.TAG, "resized image size width:" + this.originBitmap.getWidth() + ",height: " + this.originBitmap.getHeight());
        this.preview.setImageBitmap(this.originBitmap);
    }

    // Returns max width of image.
    private Integer getMaxWidthOfImage() {
        if (this.maxWidthOfImage == null) {
            if (this.isLandScape) {
                this.maxWidthOfImage = ((View) this.preview.getParent()).getHeight();
            } else {
                this.maxWidthOfImage = ((View) this.preview.getParent()).getWidth();
            }
        }
        return this.maxWidthOfImage;
    }

    // Returns max height of image.
    private Integer getMaxHeightOfImage() {
        if (this.maxHeightOfImage == null) {
            if (this.isLandScape) {
                this.maxHeightOfImage = ((View) this.preview.getParent()).getWidth();
            } else {
                this.maxHeightOfImage = ((View) this.preview.getParent()).getHeight();
            }
        }
        return this.maxHeightOfImage;
    }

    // Gets the targeted size(width / height).
    private Pair<Integer, Integer> getTargetSize() {
        Integer targetWidth;
        Integer targetHeight;
        Integer maxWidthOfImage = this.getMaxWidthOfImage();
        Integer maxHeightOfImage = this.getMaxHeightOfImage();
        targetWidth = this.isLandScape ? maxHeightOfImage : maxWidthOfImage;
        targetHeight = this.isLandScape ? maxWidthOfImage : maxHeightOfImage;
        SmartLog.i(RemoteTranslateActivity.TAG, "height:" + targetHeight + ",width:" + targetWidth);
        return new Pair<>(targetWidth, targetHeight);
    }

    @Override
    public void onDestroy() {                                                            //todo 翻译完成释放资源
        super.onDestroy();
        if (this.textAnalyzer != null) {
            try {
                this.textAnalyzer.close();
            } catch (IOException e) {
                SmartLog.e(RemoteTranslateActivity.TAG, "Stop analyzer failed: " + e.getMessage());
            }
        }
        if (this.translator != null) {
            this.translator.stop();
        }
        this.imageUri = null;
        this.path = null;
        this.srcLanguage = "Auto";
        this.dstLanguage = "EN";
    }

    @Override
    public void onSaveInstanceState(Bundle outState) { //UI界面的生命流程监听
        super.onSaveInstanceState(outState);
        outState.putParcelable(RemoteTranslateActivity.KEY_IMAGE_URI, this.imageUri);
        if (this.maxWidthOfImage != null) {
            outState.putInt(RemoteTranslateActivity.KEY_IMAGE_MAX_WIDTH, this.maxWidthOfImage);
        }
        if (this.maxHeightOfImage != null) {
            outState.putInt(RemoteTranslateActivity.KEY_IMAGE_MAX_HEIGHT, this.maxHeightOfImage);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    } //activity获得用户焦点，在与用户交互
}
