package jp.rainbowdevil.textdetection

import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import com.google.api.gax.core.CredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import java.nio.file.Files
import java.nio.file.Paths
import com.google.api.gax.core.FixedCredentialsProvider
import net.reduls.sanmoku.Tagger
import android.R.attr.bitmap
import android.net.Uri
import android.os.Environment
import android.widget.TextView
import jp.rainbowdevil.textdetection.R.raw.eng
import jp.rainbowdevil.textdetection.R.raw.math
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.*
import android.graphics.BitmapFactory
import android.os.Handler


class MainActivity : AppCompatActivity() {

    val dict = Dict()
    lateinit var math: String
    lateinit var eng : String
    val mathList = mutableListOf<List<String>>()
    val engList = mutableListOf<List<String>>()
    val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        math = IOUtils.toString(resources.openRawResource(R.raw.math))
        eng = IOUtils.toString(resources.openRawResource(R.raw.eng))


        math.split("\n").forEach {
            val list = mutableListOf<String>()
            it.split(" ").forEach {
                list.add(it)
                dict.addWord(it)
            }
            mathList.add(list)
        }

        eng.split("\n").forEach {
            val list = mutableListOf<String>()
            it.split(" ").forEach {
                list.add(it)
                dict.addWord(it)
            }
            engList.add(list)
        }

        Log.d("kitamura", "辞書サイズ=${dict.words.size}")

        //------------------------------------

/*
        val mathImages = listOf<Int>(
            R.raw.math1,
            R.raw.math2,
            R.raw.math3,
            R.raw.math4,
            R.raw.math5,
            R.raw.math6,
            R.raw.math7,
            R.raw.math8,
            R.raw.math9,
            R.raw.math10,
            R.raw.math11,
            R.raw.math12,
            R.raw.math13,
            R.raw.math14,
            R.raw.math15,
            R.raw.math16,
            R.raw.math17,
            R.raw.math18,
            R.raw.math19,
            R.raw.math20
        )

        val engImages = listOf<Int>(
            R.raw.eng1,
            R.raw.eng2,
            R.raw.eng3,
            R.raw.eng4,
            R.raw.eng5,
            R.raw.eng6,
            R.raw.eng7,
            R.raw.eng8,
            R.raw.eng9,
            R.raw.eng10,
            R.raw.eng11,
            R.raw.eng12,
            R.raw.eng13,
            R.raw.eng14,
            R.raw.eng15,
            R.raw.eng16,
            R.raw.eng17,
            R.raw.eng18,
            R.raw.eng19,
            R.raw.eng20
        )

        engImages.forEach {
            val str = ocr(it)
            Log.d("kitamura", "$str")
        }
        */

        findViewById<Button>(R.id.button).setOnClickListener {
            val intent = Intent()
            intent.action = MediaStore.ACTION_IMAGE_CAPTURE
            val imageFileUri= Uri.parse("file:///sdcard/picture.jpg");
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, imageFileUri);
            startActivityForResult(intent, 0)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        findViewById<TextView>(R.id.textView).setText("解析処理開始...")
        Thread(Runnable {
            Log.d("kitamura", "onActivityResult")
            val jpgarr = FileUtils.readFileToByteArray(File("/storage/emulated/0/picture.jpg"))
            Log.d("kitamura", "元のサイズ=${jpgarr.size / 1000}")
            val bitmap = BitmapFactory.decodeByteArray(jpgarr, 0, jpgarr.size)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos)
            Log.d("kitamura", "圧縮後のサイズ=${baos.toByteArray().size / 1000}")
            FileUtils.writeByteArrayToFile(File("/sdcard/out.jpg"), baos.toByteArray())
            handler.post({
                findViewById<TextView>(R.id.textView).setText("テキスト抽出中...")
            })
            val ret = ocr2(baos.toByteArray())
            Log.d("kitamura", "撮影結果 $ret")
            handler.post({
                findViewById<TextView>(R.id.textView).setText("結果解析中...")
            })
            labeling(ret)
        }).start()
    }

    fun labeling(str: String) {
        val inputList = mutableListOf<String>()
        str.split(" ").forEach {
            dict.addWord(it)
            inputList.add(it)
        }

        // 判定
        Log.d("kitamura", "数学")
        val mathScore = maxScore(dict, inputList, mathList)
        Log.d("kitamura", "英語")
        val engScore = maxScore(dict, inputList, engList)
        Log.d("kitamura", "数学=$mathScore 英語=$engScore")

        var text = "判定結果 : "
        if (engScore > mathScore) {
            text = text + "英語"
        } else {
            text = text + "数学"
        }
        text = text + "\n\n数学 : ${mathScore}\n英語 : $engScore"

        handler.post({
            findViewById<TextView>(R.id.textView).setText(text)
        })
    }

    fun maxScore(dict: Dict, inputList: List<String>, testList: List<List<String>>) : Double {
        var maxScore : Double = 0.0
        val inputVector = dict.toVector(inputList)
        testList.forEach {
            val vector = dict.toVector(it)
            val v1 = DoubleArray(inputVector.size)
            val v2 = DoubleArray(inputVector.size)
            inputVector.forEachIndexed { index, i ->
                v1[index] = i.toDouble()
            }
            vector.forEachIndexed { index, i ->
                v2[index] = i.toDouble()
            }
            val score = getScore(v1, v2)
            Log.d("kitamura", "score=$score")
            maxScore = Math.max(maxScore, score)
        }
        return maxScore
    }

    fun getScore(vector1: DoubleArray, vector2: DoubleArray): Double {
        if (vector1.size != vector2.size) {
            throw IllegalArgumentException("ベクトルの次元が一致しない")
        }
        val denominator = norm(vector1) * norm(vector2)
        val numerator = innerproduct(vector1, vector2)
        return numerator / denominator
    }

    fun norm(vector: DoubleArray): Double {
        var sum = 0.0
        for (i in vector.indices) {
            sum += vector[i] * vector[i]
        }
        return Math.sqrt(sum)
    }

    fun innerproduct(vector1: DoubleArray, vector2: DoubleArray): Double {
        var sum = 0.0
        for (i in vector1.indices) {
            sum += vector1[i] * vector2[i]
        }
        return sum
    }

    fun ocr2(data: ByteArray) : String{
        var ret = ""
        val myCredentials = GoogleCredentials.fromStream(resources.openRawResource(R.raw.credential))
        val a = ImageAnnotatorSettings.newBuilder()
        a.credentialsProvider = FixedCredentialsProvider.create(myCredentials)
        ImageAnnotatorClient.create(a.build()).use({ vision ->
            // Reads the image file into memory
            val imgBytes = ByteString.copyFrom(data)

            // Builds the image annotation request
            val requests = mutableListOf<AnnotateImageRequest>()
            val img = Image.newBuilder().setContent(imgBytes).build()
            val feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()
            val request = AnnotateImageRequest.newBuilder()
                .addFeatures(feat)
                .setImage(img)
                .build()
            requests.add(request)

            // Performs label detection on the image file
            val response = vision.batchAnnotateImages(requests)
            val responses = response.getResponsesList()

            for (res in responses) {
                if (res.hasError()) {
                    Log.d("kitamura", "Error ${res.error.message}")
                    System.out.printf("Error: %s\n", res.getError().getMessage())
                }

                // OCR
                // For full list of available annotations, see http://g.co/cloud/vision/docs
                val annotation = res.getFullTextAnnotation()
                var pageText = ""
                for (page in annotation.getPagesList()) {
                    for (block in page.getBlocksList()) {
                        var blockText = ""
                        for (para in block.getParagraphsList()) {
                            var paraText = ""
                            for (word in para.getWordsList()) {
                                var wordText = ""
                                for (symbol in word.getSymbolsList()) {
                                    wordText = wordText + symbol.getText()
                                    //Log.d("kitamura","Symbol text: ${symbol.text} (confidence: ${symbol.confidence})")
                                }
                                //Log.d("kitamura","Word text: ${wordText} (confidence: ${word.confidence})")
                                paraText = String.format("%s %s", paraText, wordText)
                            }
                            // Output Example using Paragraph:
                            //Log.d("kitamura","Paragraph: ${paraText} ${para.confidence}")
                            blockText = blockText + paraText
                        }
                        pageText = pageText + blockText
                    }
                }

                // 形態素解析
                for(m in Tagger.parse(pageText)) {
                    if (m.feature.indexOf("名詞") == 0 && m.feature.indexOf("数") == -1) {
                        ret = ret + m.surface + " "
                        // Log.d("kitamura", "${m.surface} ${m.feature}")
                    }
                }
                Log.d("kitamura", "認識結果=$pageText")
            }
        })
        return ret
    }

    fun ocr(imgId: Int) : String{
        var ret = ""
        val myCredentials = GoogleCredentials.fromStream(resources.openRawResource(R.raw.credential))
        val a = ImageAnnotatorSettings.newBuilder()
        a.credentialsProvider = FixedCredentialsProvider.create(myCredentials)
        ImageAnnotatorClient.create(a.build()).use({ vision ->
            val data = readAll(resources.openRawResource(imgId))

            // Reads the image file into memory
            val imgBytes = ByteString.copyFrom(data)

            // Builds the image annotation request
            val requests = mutableListOf<AnnotateImageRequest>()
            val img = Image.newBuilder().setContent(imgBytes).build()
            val feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()
            val request = AnnotateImageRequest.newBuilder()
                .addFeatures(feat)
                .setImage(img)
                .build()
            requests.add(request)

            // Performs label detection on the image file
            val response = vision.batchAnnotateImages(requests)
            val responses = response.getResponsesList()

            for (res in responses) {
                if (res.hasError()) {
                    Log.d("kitamura", "Error ${res.error.message}")
                    System.out.printf("Error: %s\n", res.getError().getMessage())
                }

                // OCR
                // For full list of available annotations, see http://g.co/cloud/vision/docs
                val annotation = res.getFullTextAnnotation()
                var pageText = ""
                for (page in annotation.getPagesList()) {
                    for (block in page.getBlocksList()) {
                        var blockText = ""
                        for (para in block.getParagraphsList()) {
                            var paraText = ""
                            for (word in para.getWordsList()) {
                                var wordText = ""
                                for (symbol in word.getSymbolsList()) {
                                    wordText = wordText + symbol.getText()
                                    //Log.d("kitamura","Symbol text: ${symbol.text} (confidence: ${symbol.confidence})")
                                }
                                //Log.d("kitamura","Word text: ${wordText} (confidence: ${word.confidence})")
                                paraText = String.format("%s %s", paraText, wordText)
                            }
                            // Output Example using Paragraph:
                            //Log.d("kitamura","Paragraph: ${paraText} ${para.confidence}")
                            blockText = blockText + paraText
                        }
                        pageText = pageText + blockText
                    }
                }

                // 形態素解析
                for(m in Tagger.parse(pageText)) {
                    if (m.feature.indexOf("名詞") == 0 && m.feature.indexOf("数") == -1) {
                        ret = ret + m.surface + " "
                        // Log.d("kitamura", "${m.surface} ${m.feature}")
                    }
                }
            }
        })
        return ret
    }

    @Throws(IOException::class)
    fun readAll(inputStream: InputStream): ByteArray {
        val bout = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val len = inputStream.read(buffer)
            if (len < 0) {
                break
            }
            bout.write(buffer, 0, len)
        }
        return bout.toByteArray()
    }
}
