import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JList;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.EmptyBorder;
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.SwingConstants;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Set;
import java.util.Map;
import java.util.Map.Entry;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/*
** WQClient gestisce tramite GUI le richieste di un utente al Word Quizzle server.
** @Attributi:
**		-client: SocketChannel dell'utente che si connette al server.
**		-username: nome dell'utente loggato.
**		-t: thread in ascolto di richieste di sfida.
*/
public class WQClient {
	private static SocketChannel client=null;
	private static String[] username=new String[1];
	private static Thread t;
	public static void main(String[] args) {
		ByteBuffer buf=ByteBuffer.allocate(500);

		// Frame principale.
		JFrame window=new JFrame("Word Quizzle");
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setSize(600,400);
		window.setLocationRelativeTo(null);

		// Creazione dei JPanel, gestiti tramite CardLayout.
		JPanel loginPanel=new JPanel();
		loginPanel.setLayout(new BoxLayout(loginPanel,BoxLayout.Y_AXIS));
		JPanel registerPanel=new JPanel();
		registerPanel.setLayout(new BoxLayout(registerPanel,BoxLayout.Y_AXIS));
		JPanel menuPanel=new JPanel();
		menuPanel.setLayout(new BoxLayout(menuPanel,BoxLayout.Y_AXIS));
		menuPanel.setMaximumSize(new Dimension(500,500));
		JLabel lblMenu=new JLabel("Bentornato "+username[0]);
		JPanel challengePanel=new JPanel();
		challengePanel.setLayout(new BoxLayout(challengePanel,BoxLayout.Y_AXIS));
		JPanel friendsPanel=new JPanel();
		friendsPanel.setLayout(new BoxLayout(friendsPanel,BoxLayout.Y_AXIS));
		JPanel rankPanel=new JPanel();
		rankPanel.setLayout(new BoxLayout(rankPanel,BoxLayout.Y_AXIS));
		JPanel contentPane=new JPanel(new CardLayout());
		contentPane.setBorder(new EmptyBorder(5,5,5,5));

		loginPanel.setVisible(true);
		registerPanel.setVisible(false);
		menuPanel.setVisible(false);
		challengePanel.setVisible(false);
		friendsPanel.setVisible(false);
		rankPanel.setVisible(false);

		contentPane.add(loginPanel,"loginPanel");
		contentPane.add(registerPanel,"registerPanel");
		contentPane.add(menuPanel,"menuPanel");
		contentPane.add(challengePanel,"challengePanel");
		contentPane.add(friendsPanel,"friendsPanel");
		contentPane.add(rankPanel,"rankPanel");
		window.add(contentPane);

		/*************************************************************
		**															 *
		**							LOGIN							 *
		**															 *
		*************************************************************/
		JLabel lblLogin=new JLabel("Benvenuto");
		lblLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblLogin.setFont(new Font("",Font.PLAIN,20));
		loginPanel.add(lblLogin);
		loginPanel.add(Box.createVerticalStrut(20));

		JLabel lblUser=new JLabel("Username:");
		lblUser.setAlignmentX(Component.CENTER_ALIGNMENT);
		loginPanel.add(lblUser);
		JTextField usrLogin=new JTextField();
		usrLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
		usrLogin.setMaximumSize(new Dimension(250,30));
		loginPanel.add(usrLogin);
		loginPanel.add(Box.createVerticalStrut(10));

		JLabel lblPsw=new JLabel("Password:");
		lblPsw.setAlignmentX(Component.CENTER_ALIGNMENT);
		loginPanel.add(lblPsw);
		JPasswordField pswLogin=new JPasswordField();
		pswLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
		pswLogin.setMaximumSize(new Dimension(250,30));
		loginPanel.add(pswLogin);
		loginPanel.add(Box.createVerticalStrut(20));

		JPanel loginButtonsPanel=new JPanel();
		loginButtonsPanel.setLayout(new BoxLayout(loginButtonsPanel,BoxLayout.X_AXIS));
		loginButtonsPanel.setVisible(true);
		loginPanel.add(loginButtonsPanel);
		JButton btnRegister=new JButton("Registrati");
		btnRegister.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnRegister.addActionListener(e -> {
			CardLayout c=(CardLayout) contentPane.getLayout();
			c.show(contentPane,"registerPanel");
		});
		loginButtonsPanel.add(btnRegister);

		JButton btnLogin=new JButton("Accedi");
		btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnLogin.addActionListener(e -> {
			try {
				client=SocketChannel.open(new InetSocketAddress(2020));
				String req="0 "+usrLogin.getText()+" "+new String(pswLogin.getPassword());
				buf.clear();
				buf.put(req.getBytes());
				buf.flip();
				while(buf.hasRemaining())
					client.write(buf);
				buf.clear();
				client.read(buf);
				buf.flip();
				byte[] resp=new byte[buf.remaining()];
				while(buf.hasRemaining())
					buf.get(resp);
				String str=new String(resp);
				if(str.equals("OK")) {
					username[0]=usrLogin.getText();
					lblMenu.setText("Bentornato "+username[0]);
					t=new Thread(new ChallengeListener(client,window,challengePanel));
					t.start();
					CardLayout c=(CardLayout) contentPane.getLayout();
					c.show(contentPane,"menuPanel");
				}
				else
					JOptionPane.showMessageDialog(window,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);
			}
			catch(IOException exc) {
				JOptionPane.showMessageDialog(window,"Il server non è disponibile","Errore",JOptionPane.ERROR_MESSAGE);
			}
		});
		loginButtonsPanel.add(btnLogin);

		/*************************************************************
		**															 *
		**							REGISTER						 *
		**															 *
		*************************************************************/
		JLabel lblRegister=new JLabel("Registrazione:");
		lblRegister.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblRegister.setFont(new Font("",Font.PLAIN,20));
		registerPanel.add(lblRegister);
		registerPanel.add(Box.createVerticalStrut(10));

		JLabel lblRegisterInfo=new JLabel("Inserisci i seguenti dati per registrarti");
		lblRegisterInfo.setAlignmentX(Component.CENTER_ALIGNMENT);
		registerPanel.add(lblRegisterInfo);
		registerPanel.add(Box.createVerticalStrut(20));

		JLabel lblUserR=new JLabel("Username:");
		lblUserR.setAlignmentX(Component.CENTER_ALIGNMENT);
		registerPanel.add(lblUserR);
		JTextField usrRegister=new JTextField();
		usrRegister.setAlignmentX(Component.CENTER_ALIGNMENT);
		usrRegister.setMaximumSize(new Dimension(250,30));
		registerPanel.add(usrRegister);
		registerPanel.add(Box.createVerticalStrut(10));

		JLabel lblPswR=new JLabel("Password:");
		lblPswR.setAlignmentX(Component.CENTER_ALIGNMENT);
		registerPanel.add(lblPswR);
		JPasswordField pswRegister=new JPasswordField();
		pswRegister.setAlignmentX(Component.CENTER_ALIGNMENT);
		pswRegister.setMaximumSize(new Dimension(250,30));
		registerPanel.add(pswRegister);
		registerPanel.add(Box.createVerticalStrut(10));

		JLabel lblConfirmPsw=new JLabel("Conferma Password:");
		lblConfirmPsw.setAlignmentX(Component.CENTER_ALIGNMENT);
		registerPanel.add(lblConfirmPsw);
		JPasswordField pswConfirmRegister=new JPasswordField();
		pswConfirmRegister.setAlignmentX(Component.CENTER_ALIGNMENT);
		pswConfirmRegister.setMaximumSize(new Dimension(250,30));
		registerPanel.add(pswConfirmRegister);
		registerPanel.add(Box.createVerticalStrut(20));

		JPanel registerButtonsPanel=new JPanel();
		registerButtonsPanel.setLayout(new BoxLayout(registerButtonsPanel,BoxLayout.X_AXIS));
		registerButtonsPanel.setVisible(true);
		registerPanel.add(registerButtonsPanel);
		JButton btnBackToLogin=new JButton("Indietro");
		btnBackToLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnBackToLogin.addActionListener(e -> {
			CardLayout c=(CardLayout) contentPane.getLayout();
			c.show(contentPane,"loginPanel");
		});
		registerButtonsPanel.add(btnBackToLogin);

		JButton btnRegisterR=new JButton("Registrati");
		btnRegisterR.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnRegisterR.addActionListener(e -> {
			if(usrRegister.getText().equals(""))
				JOptionPane.showMessageDialog(window,"Username non valido","Attenzione",JOptionPane.WARNING_MESSAGE);
			else {
				if(pswRegister.getPassword().length==0)
					JOptionPane.showMessageDialog(window,"La password non può essere vuota","Attenzione",JOptionPane.WARNING_MESSAGE);
				else {
					if(!Arrays.equals(pswRegister.getPassword(),pswConfirmRegister.getPassword()))
						JOptionPane.showMessageDialog(window,"Le password non corrispondono","Attenzione",JOptionPane.WARNING_MESSAGE);
					else {
						boolean flag=true;
						try {
							Registry r=LocateRegistry.getRegistry(2021);
							Register reg=(Register)r.lookup(Register.SERVICE_NAME);
							flag=reg.register(usrRegister.getText(),new String(pswRegister.getPassword()));
						}
						catch(RemoteException | NotBoundException exc) {
							JOptionPane.showMessageDialog(window,"Il server non è disponibile","Errore",JOptionPane.ERROR_MESSAGE);
						}
						if(!flag)
							JOptionPane.showMessageDialog(window,"Nome utente non disponibile","Attenzione",JOptionPane.WARNING_MESSAGE);
						else {
							JOptionPane.showMessageDialog(window,"Registrazione completata","Registrazione",JOptionPane.INFORMATION_MESSAGE);
							CardLayout c=(CardLayout) contentPane.getLayout();
							c.show(contentPane,"loginPanel");
						}
					}
				}
			}
		});
		registerButtonsPanel.add(btnRegisterR);

		
		lblMenu.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblMenu.setFont(new Font("",Font.PLAIN,20));
		menuPanel.add(lblMenu);
		menuPanel.add(Box.createVerticalStrut(20));

		/*************************************************************
		**															 *
		**							CHALLENGE						 *
		**															 *
		*************************************************************/
		JButton btnChallenge=new JButton("Sfida");
		btnChallenge.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnChallenge.setMaximumSize(new Dimension(300,50));
		btnChallenge.setFont(new Font("",Font.PLAIN,15));
		btnChallenge.addActionListener(e -> {
			try {
				challengePanel.removeAll();
				JLabel lblChallenge=new JLabel("Attendi il tuo avversario...");
				lblChallenge.setAlignmentX(Component.CENTER_ALIGNMENT);
				lblChallenge.setFont(new Font("",Font.PLAIN,20));
				challengePanel.add(lblChallenge);

				JTextArea txtArea=new JTextArea();
				txtArea.setFont(new Font("",Font.PLAIN,16));
				txtArea.setEditable(false);
				JScrollPane scrollTxt=new JScrollPane(txtArea);
				scrollTxt.setMaximumSize(new Dimension(250,300));
				challengePanel.add(scrollTxt);

				JButton btnBackToMenu1=new JButton("Indietro");
				btnBackToMenu1.setAlignmentX(Component.CENTER_ALIGNMENT);
				btnBackToMenu1.addActionListener(e1 -> {
					CardLayout c=(CardLayout) contentPane.getLayout();
					c.show(contentPane,"menuPanel");
				});
				challengePanel.add(btnBackToMenu1);

				CardLayout c=(CardLayout) contentPane.getLayout();
				c.show(contentPane,"challengePanel");
				String challengedFriend=JOptionPane.showInputDialog(window,"Chi vuoi sfidare?","Sfida un amico",JOptionPane.QUESTION_MESSAGE);
				if(challengedFriend!=null) {
					if(!challengedFriend.equals("")) {
						String req="6 "+username[0]+" "+challengedFriend;
						buf.clear();
						buf.put(req.getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
						buf.clear();
						client.read(buf);
						buf.flip();
						byte[] resp=new byte[buf.remaining()];
						while(buf.hasRemaining())
							buf.get(resp);
						String str=new String(resp);

						// Gestione traduzioni per chi ha sfidato.
						if(str.contains("TR")) {
							while(str.contains("TR")) {
								lblChallenge.setText("SFIDA");
								String tr=JOptionPane.showInputDialog(window,"Traduci: "+str.replace("TR ",""),"Traduzione",JOptionPane.QUESTION_MESSAGE);
								if(tr==null)
									tr="/";
								else{
									if(tr.equals(""))
										tr="/";
								}
								String sendTr="TR "+tr;
								buf.clear();
								buf.put(sendTr.getBytes());
								buf.flip();
								while(buf.hasRemaining())
									client.write(buf);
								String translateText=str.replace("TR ","")+" --> "+tr+"\n";
								txtArea.append(translateText);
								buf.clear();
								client.read(buf);
								buf.flip();
								byte[] newTr=new byte[buf.remaining()];
								while(buf.hasRemaining())
									buf.get(newTr);
								str=new String(newTr);
							}
							if(str.contains("OK END"))
								JOptionPane.showMessageDialog(window,str.replace("OK END ",""),"Risultato",JOptionPane.INFORMATION_MESSAGE);
							else
								JOptionPane.showMessageDialog(window,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);	
						}
						else {
							JOptionPane.showMessageDialog(window,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);
							c.show(contentPane,"menuPanel");
						}
					}
					else {
						JOptionPane.showMessageDialog(window,"Non hai inserito nessun amico","Attenzione",JOptionPane.WARNING_MESSAGE);
						c.show(contentPane,"menuPanel");
					}
				}
				else
					c.show(contentPane,"menuPanel");
			}
			catch(IOException exc) {
				JOptionPane.showMessageDialog(window,"Errore nella comunicazione con il server","Errore",JOptionPane.ERROR_MESSAGE);
			}
		});
		menuPanel.add(btnChallenge);

		
		/*************************************************************
		**															 *
		**							ADD FRIEND						 *
		**															 *
		*************************************************************/
		JButton btnAddFriend=new JButton("Aggiungi Amico");
		btnAddFriend.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnAddFriend.setMaximumSize(new Dimension(300,50));
		btnAddFriend.setFont(new Font("",Font.PLAIN,15));
		btnAddFriend.addActionListener(e -> {
			try {
				String newFriend=JOptionPane.showInputDialog(window,"Con chi vuoi fare amicizia?","Aggiungi Amico",JOptionPane.QUESTION_MESSAGE);
				if(newFriend!=null) {
					if(!newFriend.equals("")){
						String req="2 "+username[0]+" "+newFriend;
						buf.clear();
						buf.put(req.getBytes());
						buf.flip();
						while(buf.hasRemaining())
							client.write(buf);
						buf.clear();
						client.read(buf);
						buf.flip();
						byte[] resp=new byte[buf.remaining()];
						while(buf.hasRemaining())
							buf.get(resp);
						String str=new String(resp);
						if(str.equals("OK"))
							JOptionPane.showMessageDialog(window,"Ora tu e "+newFriend+" siete amici!","Amico Aggiunto",JOptionPane.INFORMATION_MESSAGE);
						else
							JOptionPane.showMessageDialog(window,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);
					}
					else
						JOptionPane.showMessageDialog(window,"Non hai inserito nessun amico","Attenzione",JOptionPane.WARNING_MESSAGE);
				}
			}
			catch(IOException exc) {
				JOptionPane.showMessageDialog(window,"Errore nella comunicazione con il server","Errore",JOptionPane.ERROR_MESSAGE);
			}
		});
		menuPanel.add(btnAddFriend);

		/*************************************************************
		**															 *
		**							FRIENDLIST						 *
		**															 *
		*************************************************************/
		JLabel lblFriendlist=new JLabel("I tuoi amici:");
		lblFriendlist.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblFriendlist.setFont(new Font("",Font.PLAIN,20));
		friendsPanel.add(lblFriendlist);
		friendsPanel.add(Box.createVerticalStrut(20));

		DefaultListModel<String> friendsModel=new DefaultListModel<String>();
		JList<String> jFriendlist=new JList<String>(friendsModel);
		jFriendlist.setFont(new Font("",Font.PLAIN,15));
		jFriendlist.setOpaque(false);
		DefaultListCellRenderer renderList = new DefaultListCellRenderer();
		renderList.setOpaque(false);
		renderList.setHorizontalAlignment(SwingConstants.CENTER);
		jFriendlist.setCellRenderer(renderList);
		JScrollPane scrollFriends=new JScrollPane(jFriendlist);
		scrollFriends.setMaximumSize(new Dimension(250,300));
		scrollFriends.setOpaque(false);
		scrollFriends.getViewport().setOpaque(false);
		friendsPanel.add(scrollFriends);

		JButton btnBackToMenu=new JButton("Indietro");
		btnBackToMenu.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnBackToMenu.addActionListener(e -> {
			CardLayout c=(CardLayout) contentPane.getLayout();
			c.show(contentPane,"menuPanel");
		});
		friendsPanel.add(btnBackToMenu);

		JButton btnFriendlist=new JButton("Visualizza Amici");
		btnFriendlist.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnFriendlist.setMaximumSize(new Dimension(300,50));
		btnFriendlist.setFont(new Font("",Font.PLAIN,15));
		btnFriendlist.addActionListener(e -> {
			try {
				String req="3 "+username[0];
				buf.clear();
				buf.put(req.getBytes());
				buf.flip();
				while(buf.hasRemaining())
					client.write(buf);
				buf.clear();
				client.read(buf);
				buf.flip();
				byte[] resp=new byte[buf.remaining()];
				while(buf.hasRemaining())
					buf.get(resp);
				String str=new String(resp);
				if(str.contains("OK")) {
					JsonArray jsonArr=new Gson().fromJson(str.replace("OK ",""),JsonArray.class);
					String[] fList=new Gson().fromJson(jsonArr,String[].class);
					friendsModel.clear();
					for(String s:fList)
						friendsModel.addElement(s);
					CardLayout c=(CardLayout) contentPane.getLayout();
					c.show(contentPane,"friendsPanel");
				}
				else
					JOptionPane.showMessageDialog(window,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);
			}
			catch(IOException exc) {
				JOptionPane.showMessageDialog(window,"Il server non è disponibile","Errore",JOptionPane.ERROR_MESSAGE);
			}
		});
		menuPanel.add(btnFriendlist);

		/*************************************************************
		**															 *
		**							SCORE							 *
		**															 *
		*************************************************************/
		JButton btnScore=new JButton("Punteggio");
		btnScore.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnScore.setMaximumSize(new Dimension(300,50));
		btnScore.setFont(new Font("",Font.PLAIN,15));
		btnScore.addActionListener(e -> {
			try {
				String req="4 "+username[0];
				buf.clear();
				buf.put(req.getBytes());
				buf.flip();
				while(buf.hasRemaining())
					client.write(buf);
				buf.clear();
				client.read(buf);
				buf.flip();
				byte[] resp=new byte[buf.remaining()];
				while(buf.hasRemaining())
					buf.get(resp);
				String str=new String(resp);
				if(str.contains("OK"))
					JOptionPane.showMessageDialog(window,"Il tuo punteggio è: "+str.replace("OK ",""),"Punteggio",JOptionPane.INFORMATION_MESSAGE);
				else
					JOptionPane.showMessageDialog(window,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);
			}
			catch(IOException exc) {
				JOptionPane.showMessageDialog(window,"Errore nella comunicazione con il server","Errore",JOptionPane.ERROR_MESSAGE);
			}
		});
		menuPanel.add(btnScore);

		/*************************************************************
		**															 *
		**							RANKING 						 *
		**															 *
		*************************************************************/
		JLabel lblRanking=new JLabel("Classifica:");
		lblRanking.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblRanking.setFont(new Font("",Font.PLAIN,20));
		rankPanel.add(lblRanking);
		rankPanel.add(Box.createVerticalStrut(20));

		DefaultTableModel rankModel=new DefaultTableModel();
		JTable jRank=new JTable(rankModel);
		jRank.getTableHeader().setFont(new Font("",Font.BOLD,16));
		jRank.setFont(new Font("",Font.PLAIN,14));
		jRank.setOpaque(false);
		DefaultTableCellRenderer renderTable = new DefaultTableCellRenderer();
		renderTable.setOpaque(false);
		renderTable.setHorizontalAlignment(SwingConstants.CENTER);
		jRank.setDefaultRenderer(Object.class,renderTable);
		JScrollPane scrollRank=new JScrollPane(jRank);
		scrollRank.setMaximumSize(new Dimension(250,300));
		scrollRank.setOpaque(false);
		scrollRank.getViewport().setOpaque(false);
		rankPanel.add(scrollRank);

		JButton btnBackToMenu1=new JButton("Indietro");
		btnBackToMenu1.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnBackToMenu1.addActionListener(e -> {
			CardLayout c=(CardLayout) contentPane.getLayout();
			c.show(contentPane,"menuPanel");
		});
		rankPanel.add(btnBackToMenu1);

		JButton btnRank=new JButton("Classifica");
		btnRank.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnRank.setMaximumSize(new Dimension(300,50));
		btnRank.setFont(new Font("",Font.PLAIN,15));
		btnRank.addActionListener(e -> {
			try {
				String req="5 "+username[0];
				buf.clear();
				buf.put(req.getBytes());
				buf.flip();
				while(buf.hasRemaining())
					client.write(buf);
				buf.clear();
				client.read(buf);
				buf.flip();
				byte[] resp=new byte[buf.remaining()];
				while(buf.hasRemaining())
					buf.get(resp);
				String str=new String(resp);
				if(str.contains("OK")) {
					JsonObject obj=new Gson().fromJson(str.replace("OK ",""),JsonObject.class);
					Set<Map.Entry<String,JsonElement>> rankSet=obj.entrySet();
					rankModel.setColumnCount(0);
					rankModel.addColumn("Nome");
					rankModel.addColumn("Punteggio");
					for(Map.Entry<String,JsonElement> me:rankSet) {
						Object[] row={me.getKey(),me.getValue().getAsString()};
						rankModel.addRow(row);
					}
					CardLayout c=(CardLayout) contentPane.getLayout();
					c.show(contentPane,"rankPanel");
				}
				else
					JOptionPane.showMessageDialog(window,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);
			}
			catch(IOException exc) {
				JOptionPane.showMessageDialog(window,"Il server non è disponibile","Errore",JOptionPane.ERROR_MESSAGE);
			}
		});
		menuPanel.add(btnRank);

		/*************************************************************
		**															 *
		**							LOGOUT							 *
		**															 *
		*************************************************************/
		JButton btnLogout=new JButton("Esci");
		btnLogout.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnLogout.setMaximumSize(new Dimension(300,50));
		btnLogout.setFont(new Font("",Font.PLAIN,15));
		btnLogout.addActionListener(e -> {
			try {
				String req="1 "+username[0];
				buf.clear();
				buf.put(req.getBytes());
				buf.flip();
				while(buf.hasRemaining())
					client.write(buf);
				buf.clear();
				client.read(buf);
				buf.flip();
				byte[] resp=new byte[buf.remaining()];
				while(buf.hasRemaining())
					buf.get(resp);
				String str=new String(resp);
				if(str.equals("OK")) {
					t.interrupt();
					usrLogin.setText("");
					pswLogin.setText("");
					CardLayout c=(CardLayout) contentPane.getLayout();
					c.show(contentPane,"loginPanel");
				}
				else
					JOptionPane.showMessageDialog(window,str.replace("KO",""),"Attenzione",JOptionPane.WARNING_MESSAGE);
			}
			catch(IOException exc) {
				JOptionPane.showMessageDialog(window,"Errore nella comunicazione con il server","Errore",JOptionPane.ERROR_MESSAGE);
			}
		});
		menuPanel.add(btnLogout);

		window.setVisible(true);

	}
}