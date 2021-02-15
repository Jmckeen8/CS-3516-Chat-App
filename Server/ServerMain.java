import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

//Chat application server program - by Joshua McKeen - jdmckeen@wpi.edu

public class ServerMain extends Thread{

	private ServerSocket serverSocket;
	private int port;
	//	private boolean running = false;  //true while listening for new connections
	private List<ClientHandler> clients;


	public ServerMain(int port) {  //constructor for ServerMain
		this.port = port;
	}

	public void startServer() {
		try {
			serverSocket = new ServerSocket(port);  //create new ServerSocket port 4315
			this.start();   //EXECUTES run()
		}catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void stopServer() {  
		System.out.println("Disconnecting all currently connected clients");
		//disconnect all the clients
		synchronized(clients) {
			Iterator<ClientHandler> itr = clients.iterator();  //use iterator to loop through list
			while(itr.hasNext()) {
				ClientHandler c = itr.next();
				c.shutDownClientFromServer();  //modified version of shutDownClient for accommodating iterator here
				itr.remove();   //remove client from the list here (instead of in shutDownClient)
			}
		}

		this.interrupt();  //stop main loop
		System.out.println("Server shutting down");
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		//create the synchronized list to have a list of ClientHandlers
		clients = Collections.synchronizedList(new ArrayList<ClientHandler>());
		//		running = true;
		while (!(this.isInterrupted())) {
			try {
				System.out.println("Listening for new connections");
				Socket socket = serverSocket.accept();  //accept the next connection
				ClientHandler clientHandler = new ClientHandler(socket, clients);  //pass new connection to new clientHandler
				clientHandler.start();
				clients.add(clientHandler);

			}catch(IOException e) {
				System.out.println("Socket Closed");
			}
		}
	}

	public void getClientList() {
		StringBuilder ulsb = new StringBuilder();  //start a StringBuilder to create a string with the list
		ulsb.append("Currently connected users: ");
		synchronized(clients) {
			for(ClientHandler client: clients) {  //loop through all connected clients
				String name = client.getUsername();  //get usernames from each ClientHandler
				ulsb.append(name);  //add username to the StringBuilder
				ulsb.append(", ");
			}
		}
		String userlist = ulsb.toString();   //turn StringBuilder into regular String
		System.out.println(userlist);
	}

	public static void main(String[] args) {
		int port = 4315;
		boolean keepMenuRunning = true;
		System.out.println("Starting chat server on port " + port);

		BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

		ServerMain server = new ServerMain(port);
		server.startServer();

		System.out.println("Command line options: \"stop\" to stop server, \"list\" to view list of connected users");

		String option = "";

		while (keepMenuRunning) {
			try {
				option = console.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(option.equals("stop")) {
				server.stopServer();
				keepMenuRunning = false;
			}
			else if(option.equals("list")) {
				server.getClientList();
				option = "";
			}else {
				System.out.println("Not a recognized command.");
				System.out.println("Command line options: \"stop\" to stop server, \"list\" to view list of connected users");
			}
		}
	}
}

class ClientHandler extends Thread{   //a new ClientHandler thread is created for every client
	private Socket socket;    //this client's socket
	private String username;  //this client's username
	private boolean connectionRunning;  //run loop control for this client
	private List<ClientHandler> allClients; //reference to client list

	private ObjectInputStream in;  //input stream
	private ObjectOutputStream out;  //output stream

	ClientHandler (Socket s, List<ClientHandler> l){	//constructor for ClientHandler
		socket = s;		               //socket passed in from ServerSocket.accept() via ServerMain.run()
		username = "";                 //username starts off blank, to be established later with the client
		connectionRunning=true;        //loop control starts off true
		allClients = l;                //client list reference passed in from ServerMain
		in = null;  //in and out streams created upon execution of run()
		out = null;
	}

	public void run() {
		System.out.println("Establishing new ClientHandler thread");

		//set up input and output streams for this client
		try {
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}

		//THIS IS THE MAIN LOOP
		while(connectionRunning) {
			Message msg = null;
			try {
				msg = (Message)in.readObject();   //retrieve message from readObject
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("Socket for user " + username + " closed.");
				if(connectionRunning) {  //this is true if exception was triggered by an unexpected disconnect, false if triggered by shutDownClient
					this.removeClient();
				}
				break;
			}
			//at this point we have a message from the client
			//work through message possibilities below

			//client is trying to set up username:
			if(msg.getUsernameCheck()) {
				String desiredName = msg.getFromUsername();  //get desired username from message
				if(allClients.size() == 1) {   //if there are no other clients
					username = desiredName;    //username is automatically accepted
					Message response = new Message("", desiredName, "", false, false, false, false, false);
					this.sendMessage(response);
					//log in the server console
					System.out.println("Connection with username " + username + " estabished.");
					//tell all the connected clients
					Message broadcast = new Message("Server", "", ("User " + username + " has connected."));
					synchronized(allClients) {
						for(ClientHandler client: allClients) {
							client.sendMessage(broadcast);
						}
					}
				}else {
					boolean nameOk = true;  //gets set to false if name is duplicate of another client's name
					synchronized(allClients) {
						for (ClientHandler client: allClients) {
							if(client.getUsername().equals(desiredName)) {  //if desired name is in use by another client
								nameOk = false;  //name is not ok
							}
						}
					}
					if(nameOk == true) {   //if username passed the test (not taken) construct and send response to client
						username = desiredName;   //username is accepted
						Message response = new Message("", desiredName, "", false, false, false, false, false);
						this.sendMessage(response);
						//log in the server console
						System.out.println("Connection with username " + username + " estabished.");
						//tell all the clients
						Message broadcast = new Message("Server", "", ("User " + username + " has connected."));
						synchronized(allClients) {
							for(ClientHandler client: allClients) {
								client.sendMessage(broadcast);
							}
						}
					}else {   //otherwise tell client they need to pick another name
						System.out.println("User needs to choose another name");  //for console log
						Message response = new Message("", desiredName, "", false, true, false, false, false);
						this.sendMessage(response);
					}
				}
			}

			//client is asking for a user list
			else if(msg.getUsersList()) {
				System.out.println("User " + username + " is requesting a list of all users");
				StringBuilder ulsb = new StringBuilder();  //start a StringBuilder to create a string with the list
				ulsb.append("Currently connected users: ");
				synchronized(allClients) {
					for(ClientHandler client: allClients) {  //loop through all connected clients
						String name = client.getUsername();  //get usernames from each ClientHandler
						ulsb.append(name);  //add username to the StringBuilder
						ulsb.append(", ");
					}
				}
				String userlist = ulsb.toString();   //turn StringBuilder into regular String
				Message listResponse = new Message("Server", "", userlist);  //build message
				this.sendMessage(listResponse);   //send the message to the client for THIS thread
				System.out.println("User list was sent to " + username);
			}

			//client is asking to disconnect
			else if(msg.getStopConnection()) {
				System.out.println("User " + username + " is requesting to disconnect");
				this.shutDownClientConnection();
			}

			//client wants to send a chat message
			else {
				if(!(msg.getToUsername().isEmpty())) {  //if user wants to whisper a message to a particular user
					ClientHandler targetClient = null;  //this reference to be used for target client
					synchronized(allClients) {
						for(ClientHandler client: allClients) {  //loop through client list
							if(client.getUsername().equals(msg.getToUsername())) {  //and find the client with the desired username
								targetClient = client;  //set the target client to this client
							}
						}
					}
					if(targetClient == null) {  //if the target client was not found (no user with desired username)
						this.sendMessage(new Message("", "", "", false, false, false, true, false)); //tell the client the target user isn't online
						System.out.println(username + " tried to whisper to nonexistent user " + msg.getToUsername());
					}else {
						targetClient.sendMessage(msg);  //pass the message along to the target client
						this.sendMessage(msg);          //let the sending user see the message as well (in their chat window)
						System.out.println("User " + username + " whispered to " + msg.getToUsername() + " message: " + msg.getChatMessage());
					}
				}
				else { //user wants to send a regular broadcast chat message
					synchronized(allClients) {
						for(ClientHandler client: allClients) {		//send message to all connected clients (including this one)
							client.sendMessage(msg);
						}
					}
					System.out.println(msg.getFromUsername() + ": " + msg.getChatMessage());
				}
			}
		}
	}

	//this method responsible for sending all messages to this client (from this ClientHandler)
	//may be called by THIS client's run() method, another client's run() method, or the ServerMain run() method
	public void sendMessage(Message m) {
		try {
			out.writeObject(m);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//method for server and other ClientHandlers to get username for this client
	public String getUsername() {
		return username;
	}

	//to shut down this client's connection
	public void shutDownClientConnection() {
		//send disconnect message to client
		Message msg = new Message("", "", "", false, false, false, false, true); 
		this.sendMessage(msg);

		//close streams and socket
		try {
			in.close();
			out.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}  

		//removing this client from the client list
		synchronized(allClients) {
			Iterator<ClientHandler> itr = allClients.iterator();  //use iterator to loop through list
			while(itr.hasNext()) {
				ClientHandler c = itr.next();
				if(c.getUsername().equals(username)) {  //if item in list has same username (aka is the same clientHandler)
					itr.remove();               //remove the clientHandler from the list
				}
			}
		}

		System.out.println("Connection with user " + username + " has been terminated. Thread for this client will stop now");
		Message broadcast = new Message("Server", "", ("User " + username + " has disconnected."));
		synchronized(allClients) {
			for(ClientHandler client: allClients) {
				client.sendMessage(broadcast);
			}
		}
		connectionRunning = false;
	}
	//to shut down this client's connection, modified for server shutdown sequence
	public void shutDownClientFromServer() {
		//send disconnect message to client
		Message msg = new Message("", "", "", false, false, false, false, true); 
		this.sendMessage(msg);

		//close streams and socket
		try {
			in.close();
			out.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}  

		System.out.println("Connection with user " + username + " has been terminated. Thread for this client will stop now");

		connectionRunning = false;
	}

	public void removeClient() {  //this method is used when a user has unexpectedly disconnected and needs to be removed from the client list
		//removing this client from the client list
		synchronized(allClients) {
			Iterator<ClientHandler> itr = allClients.iterator();  //use iterator to loop through list
			while(itr.hasNext()) {
				ClientHandler c = itr.next();
				if(c.getUsername().equals(username)) {  //if item in list has same username (aka is the same clientHandler)
					itr.remove();               //remove the clientHandler from the list
				}
			}
		}

		System.out.println("Connection with user " + username + " has been terminated. Thread for this client will stop now");
		Message broadcast = new Message("Server", "", ("User " + username + " has disconnected."));
		synchronized(allClients) {
			for(ClientHandler client: allClients) {
				client.sendMessage(broadcast);
			}
		}
		connectionRunning = false;
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