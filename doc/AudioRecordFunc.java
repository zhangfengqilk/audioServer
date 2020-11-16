package com.gds.robot.audio;


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioRecordFunc {
    // 缓冲区字节大小
    public int bufferSizeInBytes = 0;//

    //AudioName裸音频数据文件 ，麦克风
    private String AudioName = "";

    //NewAudioName可播放的音频文件
    private String NewAudioName = "";

    private AudioRecord audioRecord;
    private boolean isRecord = false;// 设置正在录制的状态

    private MediaPlayer mediaPlayer;

    private static AudioRecordFunc mInstance;

    private AudioRecordFunc() {

    }

    public synchronized static AudioRecordFunc getInstance() {
        if (mInstance == null)
            mInstance = new AudioRecordFunc();
        return mInstance;
    }

    public int startRecordAndFile() {
        //判断是否有外部存储设备sdcard
        if (AudioFileFunc.isSdcardExit()) {
            if (isRecord) {
                return ErrorCode.E_STATE_RECODING;
            } else {
                if (audioRecord == null)
                    creatAudioRecord();

                audioRecord.startRecording();
                // 让录制状态为true
                isRecord = true;
                // 开启音频文件写入线程
                new Thread(new AudioRecordThread()).start();

                return ErrorCode.SUCCESS;
            }

        } else {
            return ErrorCode.E_NOSDCARD;
        }

    }

    public void stopRecordAndFile() {
        close();
    }

 /*   public void playRecord(String path) {
        isRecord = false;//录制为false   停止录制内容
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(path);
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.prepare();
                mediaPlayer.start();
            } else {
                mediaPlayer.pause();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    public long getRecordFileSize() {
        return AudioFileFunc.getFileSize(NewAudioName);
    }

    private void close() {
        if (audioRecord != null) {
            System.out.println("stopRecord");
            isRecord = false;//停止文件写入
            audioRecord.stop();
            audioRecord.release();//释放资源
            audioRecord = null;
        }
    }
    /* *//**
     * 停止录音
     *//*
    private void stopRecord() {
        if (audioRecord != null) {
            blnRecord = false;
            audioRecord.stop();
            audioRecord.release();

            audioRecord = null;
            threadRecord = null;
        }*/

    private void creatAudioRecord() {
        // 获取音频文件路径
        AudioName = AudioFileFunc.getRawFilePath();
        NewAudioName = AudioFileFunc.getWavFilePath();

        // 获得缓冲区字节大小
        bufferSizeInBytes = AudioRecord.getMinBufferSize(
                AudioFileFunc.AUDIO_SAMPLE_RATE,
                AudioFileFunc.AUDIO_CHANNEL_CONFIG,
                AudioFileFunc.AUDIO_FORMAT);

        // 创建AudioRecord对象
        audioRecord = new AudioRecord(
                AudioFileFunc.AUDIO_INPUT,
                AudioFileFunc.AUDIO_SAMPLE_RATE,
                AudioFileFunc.AUDIO_CHANNEL_CONFIG,
                AudioFileFunc.AUDIO_FORMAT,
                bufferSizeInBytes);
    }


    class AudioRecordThread implements Runnable {
        @Override
        public void run() {
            writeDateTOFile();//往文件中写入裸数据
            copyWaveFile(AudioName, NewAudioName);//给裸数据加上头文件
        }
    }

    /**
     * 这里将数据写入文件，但是并不能播放，因为AudioRecord获得的音频是原始的裸音频，
     * 如果需要播放就必须加入一些格式或者编码的头信息。但是这样的好处就是你可以对音频的 裸数据进行处理，比如你要做一个爱说话的TOM
     * 猫在这里就进行音频的处理，然后重新封装 所以说这样得到的音频比较容易做一些音频的处理。
     */
    private void writeDateTOFile() {
        // new一个byte数组用来存一些字节数据，大小为缓冲区大小
        byte[] audiodata = new byte[bufferSizeInBytes];
        FileOutputStream fos = null;
        int readsize = 0;
        try {
            File file = new File(AudioName);
            if (file.exists()) {
                file.delete();
            }
            fos = new FileOutputStream(file);// 建立一个可存取字节的文件
        } catch (Exception e) {
            e.printStackTrace();
        }
        while (isRecord == true) {
            readsize = audioRecord.read(audiodata, 0, bufferSizeInBytes);
            if (AudioRecord.ERROR_INVALID_OPERATION != readsize && fos != null) {
                try {
                    fos.write(audiodata);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            if (fos != null)
                fos.close();// 关闭写入流
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 这里得到可播放的音频文件
    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        //采样率
        long longSampleRate = AudioFileFunc.AUDIO_SAMPLE_RATE;
        //声道
        int channels;
        if (AudioFileFunc.AUDIO_CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO) {
            channels = 2;
        } else {
            channels = 1;
        }
        //传输速率 [WAV文件所占容量=（采样频率×采样位数×声道）×时间/8（1字节=8bit）]
        long byteRate = 16 * AudioFileFunc.AUDIO_SAMPLE_RATE * channels / 8;
        byte[] data = new byte[bufferSizeInBytes];
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 这里提供一个头信息。插入这些信息就可以得到可以播放的文件。
     * 为我为啥插入这44个字节，这个还真没深入研究，不过你随便打开一个wav
     * 音频的文件，可以发现前面的头文件可以说基本一样哦。每种格式的文件都有
     * 自己特有的头文件。
     */
    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        //4byte 资源交换文件标志(RIFF)
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        //4byte 从下个地址开始到文件尾的总字节数
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        //4byte WAV文件标志(WAVE)
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        //4byte 波形格式标志('fmt ')
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        //4byte 过滤字节
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //2byte 格式种类(值为1时，表示数据为线性PCM编码)
        header[20] = 1; // format = 1
        header[21] = 0;
        //2byte 通道数 单1、双2
        header[22] = (byte) channels;
        header[23] = 0;
        //4byte 采样频率
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        //4byte 波形传输速率（每秒平均字节数）
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        //2byte DATA数据块长度，字节
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        //2byte PCM位宽
        header[34] = 16; // bits per sample
        header[35] = 0;
        //4byte “fact”,该部分一下是可选部分，即可能有，可能没有,一般到WAV文件由某些软件转换而成时，包含这部分。
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        //4byte size
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }
}
