package com.gds.robot.audio;
import javax.swing.JOptionPane;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.text.DecimalFormat;
import javax.sound.sampled.*;

interface Module
{
	TextArea display=new TextArea(12,65);//这是一个文本域用来显示聊天内容
	TextArea write=new TextArea(8,65);//用来输入聊天内容的文本域
	Frame f=new Frame("charFrame");//框架
	Button send=new Button("send");//发送按钮 用来发送消息
	Button close=new Button("close");//关闭按钮 用来关闭对话框
	Button talk=new Button("chart");//语言聊天按钮 用来语音聊天
	Button accept=new Button("accept");//接受按钮 用来接受对方语音聊天
	Button reject=new Button("refuse");//拒绝按钮 用来拒绝对方的语音聊天
	String IP="192.168.0.55";//IP 地址 这是对方的IP 在程序运行的时候要进行修改
	Capture cap=new Capture(IP);//Capture 是一个捕获声音并且将声音发送到目的IP地址的类
	Playback player=new Playback();//Playback 是一个接受声音并且播放声音的类
}



public class Windows extends Frame implements ActionListener,Module
{

	static	boolean begin=true;
	Windows()
	{
		super("聊天工具");
		f.setSize(500,500);//设置框架大小为长500 宽500
		f.setLocation(300,300);
		f.setFont(new Font("Arial",Font.PLAIN,12));//设置字体 为Font.PLAIN 大小为12
		f.setLayout(new FlowLayout());//布局类型设置为FlowLayout
		display.setEditable(false);//将显示聊天内容的框架设置为不可编辑
		f.add(display);//将相应的控件添加到框架中去
		f.add(write);
		f.add(send);
		f.add(talk);
		f.add(close);
		send.addActionListener(this);//下面几句话都是给控件加上监听器
		close.addActionListener(this);
		talk.addActionListener(this);
		accept.addActionListener(this);
		reject.addActionListener(this);
		f.show();//将框架显示出来
	}




	public static void main(String args[])throws IOException
	{


		new Windows();
		new RecieveThread().start();//接受对方信息的线程  这个线程接受的都是聊天的信息
		new TalkReceiveRequestThread().start();	//接受对方一些请求的信息 比如语言聊天 以及拒绝语言聊天的信息


	}




	public void actionPerformed(ActionEvent e)
	{

		if(e.getSource()==send)//如果是命令是发送则启动发送线程 并且将要发送的内容添加到display面板中去
		{
			String s;
			if((write.getText().toString()).equals(""))
			{

				JOptionPane.showMessageDialog(null,"发送的内同不能为空!","注意",JOptionPane.INFORMATION_MESSAGE);//如果发送内容是空的 则禁止发送
			}
			else
			{
				display.append("\n老大:"+(new Date().toString())+"\n"+write.getText());//这是显示当前时间
				new SendThread().start();
			}


		}

		if(e.getSource()==close)//如果命令是关闭 则整个程序结束
		{

			System.exit(0);

		}
		if(e.getSource()==talk)//如果命令是语音聊天 进行判断 第一次点击该命令的时候是请求语聊  第二次则是关闭语聊
		{
			if(begin)//如果是第一次 则是请求语聊
			{
				display.append("\n正与对方建立连接！");
				talk.setLabel("closeChart");//设置按钮的内容
				begin=false;
				new TalkRequestThread("talkstart").start();	//发出一个语音聊天请求
			}
			else
			{
				display.append("\n已经断开连接！");//断开语音聊天 并且发出一个断开请求
				talk.setLabel("语音聊天");
				begin=true;
				new TalkRequestThread("talkended").start();
				cap.stop();//因为要断开语言聊天 则接语音收线程要断开  发送线程也要断开
				player.stop();



			}
		}
		if(e.getSource()==accept)//如果命令是接受  该命令表示答应对方的语音聊天
		{
			f.remove(accept);//将两个按钮去除
			f.remove(reject);
			f.add(talk);
			new TalkRequestThread("acceptted").start();//发送一个命令 表示已经答应对方的语音聊天请求
			display.append("\n已与对方建立连接！");
			try
			{

				cap.start(); //发送语音线程启动
				player.start();//接收语音线程启动
			}
			catch(Exception f)
			{
			}
			talk.setLabel("关闭语音");//设置语音聊天按钮的内容 此时只能是关闭语音聊天
			Windows.begin=false;

		}
		if(e.getSource()==reject)//如果得到的命令是拒绝 则发出一个消息 表示拒绝对方的语音聊天
		{
			f.remove(accept);
			f.remove(reject);
			f.add(talk);
			new TalkRequestThread("rejectted").start();
		}

	}


}




class SendThread extends Thread implements Module
{
	public SendThread()
	{
		super("发送线程");

	}


	byte [] buf=new byte[256];
	DatagramSocket socket=null;
	DatagramPacket packet=null;

	public void run()
	{
		try
		{
			String s=write.getText();//将文本域里面的内容提取出来
			write.setText(null);
			byte [] buf=new byte[256];
			buf=s.getBytes();//将文本域里的内容转换为类型
			packet=new DatagramPacket(buf,buf.length,InetAddress.getByName(IP),4445);
			socket=new DatagramSocket();
			socket.send(packet);//将内容通过4445端口发送到目的IP地址的电脑上去

		}
		catch(IOException e)
		{
			display.append("\n对方没有在线!");
		}
	}

}//该线程是根据发送按钮来启动的  每次点击发送按钮 就用该线程将当前的聊天输入框里面的内容发送出去



class RecieveThread extends Thread implements Module//
{

	public RecieveThread()
	{
		super("接收线程");
	}


	byte [] buf=new byte[256];
	DatagramSocket socket=null;
	DatagramPacket packet=null;


	public void run()
	{
		try
		{
			socket=new DatagramSocket(4445);
			packet=new DatagramPacket(buf,buf.length);
		}
		catch(IOException e)
		{

		}
		while(true)//通过循环来接收源IP地址传来的内容 只要线程启动 就不停的接收 一旦接收到内容 就进行显示
		{
			try
			{
				socket.receive(packet);
				String received=new String(packet.getData());//输出当前的时间
				String s="\n楼新月: "+(new Date().toString())+"\n"+received;//将获得的信息输出
				display.append(s);
			}
			catch(IOException e)
			{

			}



		}

	}

}



class TalkRequestThread extends Thread implements Module//语音聊天请求线程 通过该线程可以发现一些应答命令 命令都是9个字节
{

	DatagramSocket socket=null;
	DatagramPacket packet=null;
	String s=null;

	public TalkRequestThread(String s)
	{
		super("语音聊天请求线程");
		this.s=s;

	}

	public void run()
	{
		byte buf[]=new byte[9];
		buf=s.getBytes();
		try
		{
			packet=new DatagramPacket(buf,buf.length,InetAddress.getByName(IP),2008);
			socket=new DatagramSocket();
			socket.send(packet);
		}
		catch(Exception e)
		{
		}

	}
}



class TalkReceiveRequestThread extends Thread implements Module//应答线程  接收命令 并且根据命令的内容判断具体的请求
{

	public TalkReceiveRequestThread()
	{
		super("语音聊天应答线程");

	}

	byte [] buf=new byte[9];
	DatagramSocket socket=null;
	DatagramPacket packet=null;
	String mgs=null;

	public void run()
	{
		try
		{
			socket=new DatagramSocket(2008);
			packet=new DatagramPacket(buf,buf.length);
		}
		catch(Exception f)
		{
		}
		while(true)// 启动该线程后就不断接收消息 接收到一条就处理一条
		{
			try
			{
				socket.receive(packet);
				mgs=new String(packet.getData());
				System.out.println(mgs);
			}
			catch(IOException e)
			{
			}

			if(mgs.equals("talkstart"))//表示对方请求语音聊天
			{
				f.remove(talk);
				f.add(accept);
				f.add(reject);
				f.show();
				display.append("\n对方请求语音聊天！");
			}
			else if(mgs.equals("acceptted"))//表示对方已经接收语音聊天
			{
				display.append("\n已与对方建立语音连接！");
				try
				{
					cap.start();
					player.start();
				}
				catch(Exception f)
				{
				}
			}

			else if(mgs.equals("rejectted"))//表示对方拒绝了语音聊天
			{
				Windows.begin=true;
				talk.setLabel("语音聊天");
				display.append("\n对方拒绝语音聊天！");
			}
			else if(mgs.equals("talkended"))//表示对方结束了语音聊天
			{
				f.remove(accept);
				f.remove(reject);
				talk.setLabel("语音聊天");
				Windows.begin=true;
				cap.stop();
				player.stop();
				display.append("\n已与对方断开连接！");
			}
		}
	}

}




class Capture implements Runnable //音频捕捉以及发送的程序
{

	TargetDataLine line;
	Thread thread;
	DatagramPacket pacToSend;
	DatagramSocket soc;

	String ip;

	/** Creates a new instance of UDPCapture */
	public Capture(String ip)
	{
		this.ip=ip;
	}
	public void start()
	{

		thread = new Thread(this);
		thread.setName("Capture");
		thread.start();
	}

	public void stop()
	{
		thread = null;
	}

	public void run()
	{

		try
		{
			soc = new DatagramSocket();
			//建立输出流 此处可以加套压缩流用来压缩数据
		} catch (Exception ex) {
			return;
		}

		AudioFormat format = new AudioFormat(8000, 16, 2, true, true);
		//audioformat(float samplerate, int samplesizeinbits, int channels,
		// boolean signed, boolean bigendian）
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

		try
		{
			line = (TargetDataLine) AudioSystem.getLine(info);
			//TargetDataLine 接口是DataLine接口的一种，通过它就可以直接从音频硬件获取数据了
			line.open(format, line.getBufferSize());
		}
		catch (Exception ex)
		{
			return;
		}

		byte[] data = new byte[1024];//跟下面的1024应保持一致
		int numbytesread = 0;
		line.start();

		while (thread != null)
		{
			numbytesread = line.read(data, 0, 1024);

			try
			{
				pacToSend = new DatagramPacket(data,data.length,
						InetAddress.getByName(ip),20001);
				soc.send(pacToSend);//写入网络流
			} catch (Exception ex)
			{
				break;
			}
		}

		line.stop();
		line.close();
		line = null;

	}


}


//下面是音频输出的代码：

class Playback implements Runnable
{

	final int bufsize = 16384; //缓存大小
	SourceDataLine line;
	Thread thread;
	DatagramSocket socket;

	byte buffer[] = new byte[1024];
	DatagramPacket pac = new DatagramPacket(buffer , buffer.length);
	/** Creates a new instance of UDPPlay */
	public Playback()
	{
	}
	public void start()
	{
		thread = new Thread(this);
		thread.setName("Playback");
		thread.start();
	}

	public void stop()
	{
		thread = null;
	}

	public void run()
	{

		try
		{
			socket = new DatagramSocket(20001);

		}
		catch(Exception e)
		{
			System.out.println("Socket Error");
		}

		AudioFormat format = new AudioFormat(8000, 16, 2, true, true);
		//audioformat(float samplerate(采样率）,
		//int samplesizeinbits, int channels, boolean signed, boolean

		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		try
		{
			line = (SourceDataLine) AudioSystem.getLine(info);
			line.open(format, bufsize);
		} catch (LineUnavailableException ex)
		{
			return;
		}

		int numbytesread = 0;
		line.start();

		while (thread != null)
		{
			try
			{
				socket.receive(pac);
				numbytesread = pac.getData().length;
				line.write(pac.getData(), 0, numbytesread);
				//write(byte[] b, int off, int len)
				//Writes audio data to the mixer via this source data line
			} catch (Exception e)
			{
				break;
			}
		}

		if (thread != null) {
			line.drain();
		}

		line.stop();
		line.close();
		line = null;
	}
}
    







