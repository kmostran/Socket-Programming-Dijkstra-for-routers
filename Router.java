import java.io.BufferedReader;
import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Router Class
 * 
 * Router implements Dijkstra's algorithm for computing the minimum distance to all nodes in the network
 * @author      Kyle Ostrander
 * @version     3.0
 * References: My notes on dijkstra's from CPSC 331 taught by Micheal John Jacobson Jr. I also made 
 * reference to Data Structures and Algorithms in java and http://www.sanfoundry.com/java-program-mplement-dijkstras-algorithm-using-priority_queue/.
 */
public class Router 
{
	private int[] distanceVector;
	private int[] prevVector;
	private int[] ports;
	private boolean[] neighborsVector;	//true if node is neighbor, false if it isn't
	private boolean[] sentToNode;
	private boolean[] receivedInfo;
	private LinkState routerLinkState;
	private int networkSize;
	private String peerIP;
	private InetAddress address;
	private int routerID;
	private int port;
	private int neighborUpdate;
	private int routeUpdate;
	private boolean done;
	private int[][] distanceMatrix; //Matrix representing the edges/adjacency of the network
	
	private DatagramSocket clientSocket;
	DatagramPacket sendPacket;
	
	private Set<Integer> settled;
    private PriorityQueue<Node> priorityQueue;
	
 	/**
 	* Constructor to initialize the program 
 	* 
 	* @param peerip		IP address of other routers (we assume that all routers are running in the same machine)
 	* @param routerid	Router ID
 	* @param port		Router UDP port number
 	* @param configfile	Configuration file name
 	* @param neighborupdate	link state update interval - used to update router's link state vector to neighboring nodes
    * @param routeupdate 	Route update interval - used to update route information using Dijkstra's algorithm 
    */
	public Router(String peerip, int routerid, int port, String configfile, int neighborupdate, int routeupdate) 
	{
		this.done = false;
		this.peerIP = peerip;
		try
		{
			this.address = InetAddress.getByName(peerIP);
		}
		catch(Exception e)
		{
			System.out.println("Error: " + e);
		}
		this.routerID = routerid;
		this.port = port;
		this.neighborUpdate = neighborupdate;
		this.routeUpdate = routeupdate;
		
		//Parse configuration file, set up link state vector for router
		String line = null;
		String[] splitLine;
        try {
            FileReader fileReader = new FileReader(configfile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            int i = 0;
            while((line = bufferedReader.readLine()) != null) 
            {
            	if(i==0)
            	{
            		networkSize = Integer.parseInt(line);
            		distanceVector = new int[networkSize];
					distanceMatrix = new int[networkSize][networkSize];
                    for(int j=0;j<networkSize;j++)
                    {
                    	distanceVector[j] = 999;
                    }
                    distanceVector[routerid] = 0;
            		neighborsVector = new boolean[networkSize];
            		sentToNode = new boolean[networkSize];
            		receivedInfo = new boolean[networkSize];
            		ports = new int[networkSize];
                    for(int j=0;j<networkSize;j++)
                    {
                    	neighborsVector[j] = false;
                    	sentToNode[j] = false;
                    	ports[j]=-1;
						receivedInfo[j] = false;
                    }
                    sentToNode[routerID] = true;
            	}
            	else
            	{
            		splitLine = line.split("\\s+");
            		distanceVector[Integer.parseInt(splitLine[1])] = Integer.parseInt(splitLine[2]);
            		neighborsVector[Integer.parseInt(splitLine[1])] = true;
            		ports[Integer.parseInt(splitLine[1])] = Integer.parseInt(splitLine[3]);
            	}
            	i++;
            }
            bufferedReader.close();         
        }
        catch(Exception e)
        {
        	System.out.println("Error: : " + e);
        }
        
        //Initialize prevVector to all contain negative values, aside from for the node itself
        prevVector = new int[networkSize];
        for(int j=0;j<networkSize;j++)
        {
        	prevVector[j] = -1;
        }
        prevVector[routerID] = routerID;
		for (int i = 0; i < distanceMatrix.length; i++)
		{
			for (int j = 0; j < distanceMatrix[i].length; j++) 
			{
				distanceMatrix[i][j] = 999;
			}
		}
		updateDistanceMatrix(routerID,distanceVector);
		
		receivedInfo[routerID]=true;
		
        settled = new HashSet<Integer>();
        priorityQueue = new PriorityQueue<Node>(networkSize,new Node());
	}
	
	//Updates the matrix based on a new distance vector, either one from another router or the one from the 
	//current router updated
	public void updateDistanceMatrix(int nodeID,int[] newInfo)
	{
		for (int i = 0; i < distanceMatrix.length; i++)
		{
			if(i==nodeID)
			{
				for (int j = 0; j < newInfo.length; j++) 
				{
					if(newInfo[j]<=distanceMatrix[i][j])
						distanceMatrix[i][j] = newInfo[j];
				}					
			}
		}
	}
	
	//Begin the algorithm as outlined in the design notes
	public void setUp()
	{
		//2. Create UDP socket to send and listen link state message
		try
		{
			clientSocket = new DatagramSocket(port);
			byte[] receiveData = new byte[1000];
			DatagramPacket receivePacket;
			
			//3. Set timer task to send node's link state vector to neighboring nodes every 1000 ms
			TimerTask newTaskPUN = new TimeOutHandlerPUN();
			Timer timerPUN = new Timer();
			timerPUN.scheduleAtFixedRate(newTaskPUN,neighborUpdate,neighborUpdate);
			
			//4. Set timer task to update node's route information every 10000 ms
			TimerTask newTaskPUR = new TimeOutHandlerPUR();
			Timer timerPUR = new Timer();
			timerPUR.scheduleAtFixedRate(newTaskPUR,routeUpdate,routeUpdate);
			
			// Causes the program to eventually end, feel free to uncomment out, otherwise just use ctrl c. I implemented Approach-1
			// Timer task that checks to see if a program has completed, runs every 100000 ms
			//TimerTask newTaskEND = new TimeOutHandlerEND();
			//Timer timerEND = new Timer();
			//timerEND.scheduleAtFixedRate(newTaskEND,routeUpdate*10,routeUpdate*10);
			
			//5. while TRUE
			while(true)
			{
				//6. Receive link state message from neighbor
				receivePacket = new DatagramPacket(receiveData,receiveData.length,address,port);
				clientSocket.receive(receivePacket);
				processUpdateDS(receivePacket);
				if(done==true)
					break;
			}
			//9. end of while
			clientSocket.close();
		}
		catch(Exception e)
		{
			System.out.println("Error in setup: " + e);
		}
	}


	/**
 	*  Compute route information based on Dijkstra's algorithm and print the same
 	* 
 	*/
	public synchronized void compute() 
	{
		//Print matrix
//		System.out.println();
//		for (int i = 0; i < distanceMatrix.length; i++)
//		{
//			for (int j = 0; j < distanceMatrix[i].length; j++) 
//			{
//				System.out.print(distanceMatrix[i][j] + " ");
//			}
//			System.out.println();
//		}
		
		//Call Dijkstra's algorithm
		dijkstra(routerID);
		
		//Update matrix
		for (int i = 0; i < distanceMatrix.length; i++)
		{
			distanceMatrix[routerID][i] = distanceVector[i];
		}
		
	  	//Print out routing table information
    	System.out.println("Routing Info for node " + routerID);
    	System.out.println("RouterID \t Distance \t Prev RouterID");
    	for(int i = 0; i < networkSize; i++)
    	{
    		System.out.println(i + "\t\t   " + distanceVector[i] +  "\t\t\t" +  prevVector[i]);
    	}		
	}
	
	//Begins the implementation of dijkstra's algorithm (using a priority queue)
	public synchronized void dijkstra(int src)
    {
        int evaluationNode;
        priorityQueue.add(new Node(src, 0));
        while (!priorityQueue.isEmpty())
        {
            evaluationNode = getNodeWithMinimumDistanceFromPriorityQueue();
            settled.add(evaluationNode);
            evaluateNeighbours(evaluationNode);
        }
    } 
 
	//Part of dijkstra's algorithm
	//Returns the node with the minimum distance in the priority queue
    private int getNodeWithMinimumDistanceFromPriorityQueue()
    {
        Node min = priorityQueue.remove();
        return min.value;
    }
 
    //Part of dijkstra's algorithm
    //Evaluates the neighbors of the node passed in
    private void evaluateNeighbours(int evaluationNode)
    {
        int edgeDistance = -1;
        int newDistance = -1;
        
        for (int destinationNode = 0; destinationNode < networkSize; destinationNode++)
        {
            if (!settled.contains(destinationNode))
            {
                if (distanceMatrix[evaluationNode][destinationNode] != 999)
                {
                	//System.out.println(destinationNode + " " + evaluationNode);
                    edgeDistance = distanceMatrix[evaluationNode][destinationNode];
                    newDistance = distanceVector[evaluationNode] + edgeDistance;
                    //Following if else calculates the third column for the data printed (previous node in least cost path)
                    if (newDistance <= distanceVector[destinationNode])
                    {
                        distanceVector[destinationNode] = newDistance;
                        prevVector[destinationNode] = evaluationNode;
                    }
                    //Neighbours/self
                    else if(prevVector[destinationNode]==-1)
                    {
                    	prevVector[destinationNode] = routerID;
                    }
                    priorityQueue.add(new Node(destinationNode,distanceVector[destinationNode]));
                }   
            }
        }
    }
	
    //Updates data structures and forwards received link states
	public synchronized void processUpdateDS(DatagramPacket receivePacket)
	{
		//7. Update data structures
		LinkState receivedLinkState;	
		receivedLinkState = new LinkState(receivePacket);
		//if(neighborsVector[receivedLinkState.sourceId]==true)
			receivedInfo[receivedLinkState.sourceId]=true;
		int origin = receivedLinkState.sourceId;
		updateDistanceMatrix(receivedLinkState.sourceId,receivedLinkState.cost);
		
		//8. Forward link state message received to neighboring node(s) based on broadcast algorithm
		for(int i=0;i<networkSize;i++)
		{
			if((neighborsVector[i]==true)&&(origin!=i))
			{
				try
				{
					sendPacket = new DatagramPacket(receivedLinkState.getBytes(),receivedLinkState.getBytes().length,address,ports[i]);
					clientSocket.send(sendPacket);
				}
				catch(Exception e)
				{
					System.out.println("Error in processUpdateDS: " + e);
				}
			}
		}
	}
	
	//Updates neighbors of the current node
	public synchronized void processUpdateNeighbor()
	{
		//Send node's link state vector to the neighboring nodes as link state message
		for(int i=0;i<networkSize;i++)
		{
			if(neighborsVector[i]==true)
			{
				try
				{
					routerLinkState = new LinkState(routerID,i,distanceVector);
					sendPacket = new DatagramPacket(routerLinkState.getBytes(),routerLinkState.getBytes().length,address,ports[i]);
					clientSocket.send(sendPacket);
				}
				catch(Exception e)
				{
					System.out.println("Error in processUpdateNeighbor: " + e);
				}
			}
		}
	}
	
	//Updates the route, calls the compute method to preform dijkstra's algoirhtm if the neccessary information has been received
	public synchronized void processUpdateRoute()
	{	
		//If link state vectors of all nodes received, compute route info based on Dijkstra's algorithm and print output
		for(int i=0;i<networkSize;i++)
		{
			//if(receivedInfo[i]!=neighborsVector[i])
			if(receivedInfo[i]!=true)
			{
				System.out.println("Didn't receive enough information from neighbors");
				return;
			}
		}
		for(int i=0;i<networkSize;i++)
			receivedInfo[i]=false;
		//
		receivedInfo[routerID]=true;
		compute();
	}

	//Calls processUodateNeighbor()
	public class TimeOutHandlerPUN extends TimerTask 
	{	
		public TimeOutHandlerPUN()
		{
		}
		
		public void run()
		{
			processUpdateNeighbor();
		}
	}

	//Calls processUpdateRoute
	public class TimeOutHandlerPUR extends TimerTask 
	{	
		public TimeOutHandlerPUR()
		{
		}
		
		public void run()
		{
			processUpdateRoute();
		}
	}
	
	//Calls checkCompletion every 100s
	public class TimeOutHandlerEND extends TimerTask 
	{	
		public TimeOutHandlerEND()
		{
		}
		
		public void run()
		{
			checkCompletion();
		}
	}
	
	//Checks to see if all values of the matrix have been calculated
	public synchronized void checkCompletion()
	{
		for (int i = 0; i < distanceMatrix.length; i++)
		{
			for (int j = 0; j < distanceMatrix[i].length; j++) 
			{
				if(distanceMatrix[i][j]==999)
					return;
			}
		}
		done = true;
	}
	
	/* A simple test driver */
	public static void main(String[] args) 
	{	
		//String peerip = "127.0.0.1"; // all router programs running in the same machine for simplicity
		String peerip = "localhost";
		String configfile = "";
		int routerid = 999;
        int neighborupdate = 1000; // milli-seconds, update neighbor with link state vector every second
		int forwardtable = 10000; // milli-seconds, print route information every 10 seconds
		int port = -1; // router port number
	
		// check for command line arguments
		if (args.length == 3) {
			// either provide 3 parameters
			routerid = Integer.parseInt(args[0]);
			port = Integer.parseInt(args[1]);	
			configfile = args[2];
		}
		else {
			System.out.println("wrong number of arguments, try again.");
			System.out.println("usage: java Router routerid routerport configfile");
			System.exit(0);
		}

		//1. Initialization of data structure(s)
		Router router = new Router(peerip, routerid, port, configfile, neighborupdate, forwardtable);
		System.out.println("Router initialized..running");
		router.setUp();
		router.compute();
		System.out.println("Program complete, exiting now");
		System.exit(0);
	}
}

//Simulates a graph based on the 2d matrix of edge distances
class Node implements Comparator<Node>
{
    public int value;
    public int cost;
 
    public Node()
    {
    }
 
    public Node(int value, int cost)
    {
        this.value = value;
        this.cost = cost;
    }
 
    @Override
    public int compare(Node node1, Node node2)
    {
        if (node1.cost < node2.cost)
            return -1;
        if (node1.cost > node2.cost)
            return 1;
        return 0;
    }
}
