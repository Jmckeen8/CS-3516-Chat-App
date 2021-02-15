import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

//Chat application client program - by Joshua McKeen - jdmckeen@wpi.edu

public class ClientMain {

	public static void main(String[] args) {

		System.out.println("Client Starting");

		//ESTABLISH CONNECTION WITH SERVER
		Socket clientSocket = null;
		try {
			clientSocket = new Socket("localhost", 4315);  //try to establish connection on port 4315
		}
		catch(Exception e) {  //if it fails, is server running?
			System.out.println("Couldn't connect to the server. Is it running?");
			System.out.println("Client exiting...");
			System.exit(0);  //exit client
		}

		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(clientSocket.getOutputStream());  //establish output stream
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(clientSocket.getInputStream());  //establish input stream
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		System.out.println("Established input and output streams");

		InputStreamReader isr = new InputStreamReader(in);  //establish new inputstreamreader
		isr.start();  //start the input stream reader

		//create a new GUI object
		new GUI(out, isr);  //the GUI has access to the out and in streams

		try {
			isr.join();  //wait for receiver thread to terminate before continuing with disconnect/shutdown
		} catch (InterruptedException e) {
			e.printStackTrace();
		} 

		System.out.println("Disconnecting streams and socket");
		try {
			in.close();     //disconnect all streams and close socket
			out.close();
			clientSocket.close();
		} catch (IOException e) {

			e.printStackTrace();
		} 
		System.out.println("Connection closed. Client terminating");
		System.exit(0);

	}

}

class GUI {
	//GUI Elements
	private JFrame frame;  //frame
	private JButton list;  //list button
	private JButton whisper;  //whisper button
	private JButton eWhisper;  //exit whisper button
	private JButton quit;   //quit button
	private JTextField chatIn;  //chat box input
	private JButton send;    //chat send button
	private JTextArea chat;   //chat box (incoming messages log)
	private JScrollPane chatScroll;  //scroll frame for chat

	//Event Listeners for GUI elements
	private sendListener sL;
	private listListener lL;
	private whisperListener wL;
	private eWhisperListener eWL;
	private quitListener qL;
	private messageListener mL;

	//input and output streams from main method
	private ObjectOutputStream out;

	private InputStreamReader isr; //input stream reader from main method

	private String whisperingTo = "";  //keeping track of who the client is whispering to
	private String username = "";  //keeping track of this client's username

	GUI(ObjectOutputStream o, InputStreamReader r){  //THIS IS THE GUI CONSTRUCTOR

		out = o;
		isr = r;

		mL = new messageListener();  //create a new message listener
		isr.setListener(mL);    //set the input stream reader to feed to the message listener

		//Establishing the four control buttons, list, whisper, exit whisper, and quit. Establishing sizes and locations
		//Buttons start disabled so the user doesn't try to click them while they're still establishing their username with the server
		list = new JButton("View User List");
		list.setSize(150, 75);
		list.setLocation(700, 100);
		lL = new listListener();
		list.addActionListener(lL);  //list action listener
		whisper = new JButton("<html>Whisper to<br>Another User</html>");
		whisper.setSize(150, 75);
		whisper.setLocation(700, 200);
		wL = new whisperListener();
		whisper.addActionListener(wL); //whisper action listener
		eWhisper = new JButton("Exit Whisper Mode");
		eWhisper.setSize(150, 75);
		eWhisper.setLocation(700, 300);
		eWL = new eWhisperListener();
		eWhisper.addActionListener(eWL);  //eWhisper action listener
		quit = new JButton("Exit Chat");
		quit.setSize(150, 75);
		quit.setLocation(700, 400);
		qL = new quitListener();
		quit.addActionListener(qL);  //quit action listener

		//Chat input for the client to send messages to the server
		chatIn = new JTextField();
		chatIn.setSize(500, 40);
		chatIn.setLocation(20, 575);
		//button for sending messages
		send = new JButton("Send Message");
		send.setSize(130, 40);
		send.setLocation(540, 575);
		sL = new sendListener();
		send.addActionListener(sL);  //send action listener

		//chat "window" or "log" where all incoming messages are displayed
		chat = new JTextArea();
		chat.setEditable(false);

		//creating new PrintStream to redirect System.out to the chat window GUI
		PrintStream GUIStream = new PrintStream(new CustomOutputStream(chat));
		System.setOut(GUIStream);
		System.setErr(GUIStream);

		//scroll wrapper for chat window so it's scrollable when there are many messages
		chatScroll = new JScrollPane(chat);
		chatScroll.setSize(650, 525);
		chatScroll.setLocation(20, 20);

		//frame, adding elements above
		frame = new JFrame();
		frame.add(list);
		frame.add(whisper);
		frame.add(eWhisper);
		frame.add(quit);
		frame.add(chatIn);
		frame.add(send);
		frame.add(chatScroll);
		frame.getRootPane().setDefaultButton(send);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  //exit program if [X] button is clicked
		frame.setLayout(null);
		frame.setSize(900, 700);  //overall frame size
		frame.setTitle("Chat");  //program name
		frame.setVisible(true);

		setUpUsername();


	}
	//listener class for send button
	private class sendListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent arg0) {
			String chatString = chatIn.getText();   //get the text from the chat input
			chatIn.setText("");  //reset the chat input box to blank

			Message msg = new Message(username, whisperingTo, chatString);  //construct regular message object
			sendMessage (msg);

		}

	}

	//listener class for list button
	private class listListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent arg0) {
			Message msg = new Message("", "", "", false, false, true, false, false); //request server to send user list
			sendMessage(msg);
		}

	}

	//listener class for whisper button
	private class whisperListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent arg0) {
			String desiredWhisper = JOptionPane.showInputDialog(frame, "Which user would you like to whisper to?");
			if(desiredWhisper==null) {  //if user hits cancel button, don't change anything
				//do nothing
			}
			else if(desiredWhisper.isEmpty()) {  //if user enters blank whisper, set whisper to blank
				whisperingTo="";
			}
			else {
				whisperingTo = desiredWhisper;  //if user enters a username, set whisperingTo
			}
			System.out.println("Now whispering to: " + whisperingTo); //confirmation message in chat window
			System.out.print("> ");

		}

	}

	//listener class for eWhisper button
	private class eWhisperListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent arg0) {
			whisperingTo = "";  //set whisperingTo to be blank (broadcast message)
			System.out.println("Whispering mode disabled. Your messages will now be sent to everyone.");  //confirmation in chat window
			System.out.print("> ");
		}

	}

	//listener class for quit button
	private class quitListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent arg0) {
			Message msg = new Message("", "", "", false, false, false, false, true); //request to stop connection
			sendMessage(msg);
		}

	}

	//listener class for incoming messages
	private class messageListener implements ChangeListener{

		@Override
		public void stateChanged(ChangeEvent e) {
			Message msg = (Message)e.getSource();  //pull the message from the listener
			if(msg.getNeedAnotherName()) {  //if the server reports the user needs to choose another username
				setUpUsernameAgain();
			}
			else if(msg.getWhisperNotExist()) {
				JOptionPane.showMessageDialog(frame, "The user you have tried to whisper to is not connected. The chat will now exit whisper mode."); 
				whisperingTo="";
				System.out.println("Whispering mode disabled. Your messages will now be sent to everyone.");  //confirmation in chat window
				System.out.print("> ");
			}
			else if(!(msg.getChatMessage().isEmpty())) {  //if there is a message body
				System.out.println(msg.getFromUsername() + ": " + msg.getChatMessage());
				System.out.print("> ");
			}
		}

	}

	private void sendMessage(Message msg) {
		try {
			out.writeObject(msg);
		} catch (IOException e) {			
			e.printStackTrace();
		}
	}
	private void setUpUsername() {
		String desiredName = "";
		boolean nameChosen = false;
		while (nameChosen == false) {   //while loop to make sure user actually enters a username
			desiredName = JOptionPane.showInputDialog(frame, "What would you like your username to be?");
			if(desiredName == null) {
				Message msg = new Message("", "", "", false, false, false, false, true); //request to stop connection
				sendMessage(msg);
				nameChosen = true;  //to exit the while loop
			}
			else if(desiredName.isEmpty()) {
				//do nothing, nameChosen stays false
			}
			else {
				nameChosen = true;   //user did input a name
			}
		}
		Message msg = new Message(desiredName, "", "", true, false, false, false, false);  //construct username check message
		sendMessage(msg);
		username = desiredName;   //this client knows the username as well
	}

	private void setUpUsernameAgain() {  //to be used if username was already taken and need to ask user again
		String desiredName = "";
		boolean nameChosen = false;
		while (nameChosen == false) {   //while loop to make sure user actually enters a username
			desiredName = JOptionPane.showInputDialog(frame, "That username is already in use. Please choose another username:");
			if(desiredName == null) {
				Message msg = new Message("", "", "", false, false, false, false, true); //request to stop connection
				sendMessage(msg);
				nameChosen = true;  //to exit the while loop
			}
			else if(desiredName.isEmpty()) {
				//do nothing, nameChosen stays false
			}
			else {
				nameChosen = true;   //user did input a name
			}
		}
		Message msg = new Message(desiredName, "", "", true, false, false, false, false);  //construct username check message
		sendMessage(msg);
		username = desiredName;   //this client knows the username as well
	}
}

class CustomOutputStream extends OutputStream{
	private JTextArea chat;

	//constructor takes in JTextArea from GUI
	CustomOutputStream(JTextArea jta){
		chat = jta;
	}

	@Override
	public void write(int arg0) throws IOException {
		//send data to GUI textArea
		chat.append(String.valueOf((char)arg0));
		//move to next line
		chat.setCaretPosition(chat.getDocument().getLength());

	}
}

class InputStreamReader extends Thread{  //this thread purely responsible for receiving incoming messages from the server

	private ObjectInputStream in;   //input stream
	private ChangeListener listener;  //GUI change listener, triggers when message arrives

	InputStreamReader(ObjectInputStream i){
		in = i;
		listener = null;
	}

	@Override
	public void run() {
		boolean stillRunning = true; //running set to true upon construction
		while(stillRunning && !(this.isInterrupted())) {
			Message msg = null;
			try {
				msg = (Message)in.readObject();   //retrieve message from readObject
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {  //connection with the server has failed (aka server terminated unexpectedly)
				stillRunning = false;
				System.out.println("Server was disconnected unexpectedly!");
				break;  //break out of while loop
			}

			if(msg.getStopConnection()) {   //if stopconnection is requested either by the client (via the server) or directly by the server
				stillRunning = false;
				System.out.println("Disconnecting from server...");
			}

			ChangeEvent event = new ChangeEvent(msg);  //create new change event
			listener.stateChanged(event);   //trigger the listener with the event
		}

	}

	public ChangeListener getListener() {  //method so others can have access to the listener
		return listener;
	}

	public void setListener(ChangeListener cl) {  //method to put in listener
		listener = cl;
	}

}

//Message class is the foundation of information sent between server and clients
//All communication between server and clients is via a Message object
class Message implements Serializable{
	private static final long serialVersionUID = 1L;
	private String fromUsername;
	private String toUsername;  //blank if chatMessage is intended to be broadcasted to everyone
	private String chatMessage;

	//internal communication parameters
	private boolean usernameCheck;    //send upon initial client connection to check if username is ok
	private boolean needAnotherName;  //server sends back true if another username is needed
	private boolean usersList;        //requesting a list of users
	private boolean whisperNotExist;  //sent by server in true if client tries to whisper a nonexistant user
	private boolean stopConnection;   //sent when client/server wants the other component to close their end of the connection

	Message(String f, String t, String c){  //regular chat message
		fromUsername = f; 
		toUsername = t;
		chatMessage = c;
		usernameCheck = false;
		needAnotherName = false;
		usersList = false;
		whisperNotExist = false;
		stopConnection = false;
	}

	Message(String f, String t, String c, boolean u, boolean n, boolean l, boolean w, boolean s){  //for internal boolean flags
		fromUsername = f; 
		toUsername = t;
		chatMessage = c;
		usernameCheck = u;
		needAnotherName = n;
		usersList = l;
		whisperNotExist = w;
		stopConnection = s;
	}

	//SETTERS
	public void setFromUsername(String u) {
		fromUsername = u;
	}
	public void setToUsername(String u) {
		toUsername = u;
	}
	public void setChatMessage(String c) {
		chatMessage = c;
	}
	public void setUsernameCheck(boolean b) {
		usernameCheck = b;
	}
	public void setNeedAnotherName(boolean b) {
		needAnotherName = b;
	}
	public void setUsersList(boolean b) {
		usersList = b;
	}
	public void setWhisperNotExist(boolean b) {
		whisperNotExist = b;
	}
	public void setStopConnection(boolean b) {
		stopConnection = b;
	}

	//GETTERS
	public String getFromUsername() {
		return fromUsername;
	}
	public String getToUsername() {
		return toUsername;
	}
	public String getChatMessage() {
		return chatMessage;
	}
	public boolean getUsernameCheck() {
		return usernameCheck;
	}
	public boolean getNeedAnotherName() {
		return needAnotherName;
	}
	public boolean getUsersList() {
		return usersList;
	}
	public boolean getWhisperNotExist() {
		return whisperNotExist;
	}
	public boolean getStopConnection() {
		return stopConnection;
	}
}