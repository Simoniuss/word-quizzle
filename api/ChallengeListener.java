import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/*
** Thread che si mette in ascolto di richieste UDP di sfida e poi gestisce la sfida
** dell'utente sfidato.
** @Attributi:
**		-sockCh: SocketChannel dell'utente.
**		-dSock: DatagramSocket in ascolto di pacchetti UDP.
**		-frame: JFrame dell'utente.
**		-chPanel: JPanel della sfida.
*/

public class ChallengeListener implements Runnable {
	private SocketChannel sockCh;
	private DatagramSocket dSock;
	private JFrame frame;
	private JPanel chPanel;

	public ChallengeListener(SocketChannel s,JFrame f,JPanel p) {
		sockCh=s;
		try {
			dSock=new DatagramSocket(((InetSocketAddress)sockCh.getLocalAddress()).getPort());
		}
		catch(IOException e) {}
		frame=f;
		chPanel=p;
	}

	public void run() {
		byte[] dataBuf=new byte[500];
		ByteBuffer chBuf=ByteBuffer.allocate(500);
		while(true) {
			DatagramPacket dataPkt=new DatagramPacket(dataBuf,0,dataBuf.length);
			try {
				dSock.receive(dataPkt);
			}
			catch(IOException e){}
			byte[] rcv=new byte[dataPkt.getLength()];
			System.arraycopy(dataPkt.getData(),dataPkt.getOffset(),rcv,0,rcv.length);
			String str=new String(rcv);

			// Lettura della richiesta di sfida.
			String[] splitRcv=str.split(" ");
			if(splitRcv.length==3) {
				int opt=JOptionPane.showConfirmDialog(frame,"Hai ricevuto una sfida da "+splitRcv[1]+". Accetti?","Sfida",JOptionPane.YES_NO_OPTION);
				try {

					// Accetta sfida.
					if(opt==JOptionPane.YES_OPTION) {
						String resp="7 "+splitRcv[2]+" "+splitRcv[1]+" 1";
						chBuf.clear();
						chBuf.put(resp.getBytes());
						chBuf.flip();
						while(chBuf.hasRemaining())
							sockCh.write(chBuf);
						JPanel contentPane=(JPanel)frame.getContentPane().getComponent(0);
						CardLayout c=(CardLayout)contentPane.getLayout();
						chPanel.removeAll();

						JLabel lblChallenge=new JLabel("SFIDA");
						lblChallenge.setAlignmentX(Component.CENTER_ALIGNMENT);
						lblChallenge.setFont(new Font("",Font.PLAIN,20));
						chPanel.add(lblChallenge);

						JTextArea txtArea=new JTextArea();
						txtArea.setFont(new Font("",Font.PLAIN,16));
						txtArea.setEditable(false);
						JScrollPane scrollTxt=new JScrollPane(txtArea);
						scrollTxt.setMaximumSize(new Dimension(250,300));
						chPanel.add(scrollTxt);

						JButton btnBackToMenu=new JButton("Indietro");
						btnBackToMenu.setAlignmentX(Component.CENTER_ALIGNMENT);
						btnBackToMenu.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								c.show(contentPane,"menuPanel");
							}
						});
						chPanel.add(btnBackToMenu);

						c.show(contentPane,"challengePanel");
						chBuf.clear();
						sockCh.read(chBuf);
						chBuf.flip();
						byte[] first=new byte[chBuf.remaining()];
						while(chBuf.hasRemaining())
							chBuf.get(first);
						str=new String(first);

						// Gestione sfida.
						if(str.contains("TR")) {
							while(str.contains("TR")) {
								String tr=JOptionPane.showInputDialog(frame,"Traduci: "+str.replace("TR ",""),"Traduzione",JOptionPane.QUESTION_MESSAGE);
								if(tr==null)
									tr="/";
								else{
									if(tr.equals(""))
										tr="/";
								}
								String sendTr="TR "+tr;
								chBuf.clear();
								chBuf.put(sendTr.getBytes());
								chBuf.flip();
								while(chBuf.hasRemaining())
									sockCh.write(chBuf);
								String translateText=str.replace("TR ","")+" --> "+tr+"\n";
								txtArea.append(translateText);
								chBuf.clear();
								sockCh.read(chBuf);
								chBuf.flip();
								byte[] newTr=new byte[chBuf.remaining()];
								while(chBuf.hasRemaining())
									chBuf.get(newTr);
								str=new String(newTr);
							}
							if(str.contains("OK END"))
								JOptionPane.showMessageDialog(frame,str.replace("OK END ",""),"Risultato",JOptionPane.INFORMATION_MESSAGE);
							else
								JOptionPane.showMessageDialog(frame,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);	
						}
						else {
							JOptionPane.showMessageDialog(frame,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);
							c.show(contentPane,"menuPanel");
						}
					}

					// Sfida rifiutata.
					else {
						String resp="7 "+splitRcv[2]+" "+splitRcv[1]+" 0";
						chBuf.clear();
						chBuf.put(resp.getBytes());
						chBuf.flip();
						while(chBuf.hasRemaining())
							sockCh.write(chBuf);
					}
				}
				catch(IOException e) {
					JOptionPane.showMessageDialog(frame,"Il server non è disponibile","Errore",JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}
}