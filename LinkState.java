import java.net.*;
import java.util.*;

 
/**
 * Class LinkState
 * 
 * LinkState defines the structure of the linkstate message used to 
 * exchange routing information between the routers.
 *
 * 
 * @author 	Cyriac James
 * @version	1.0
 *
 */

public class LinkState {
	
	int sourceId; // id of the node that generates the link state message in the first place (link state vector always belong to this node)
	int destId; // id of the destination node; this changes when a node forwards the message to its neighbor
        public final static int HEADER_SIZE = Integer.SIZE/8 * 2; // bytes (integer size is 4 bytes in java)
        public final static int MAX_PAYLOAD_SIZE = Integer.SIZE/8 * 10; // bytes (i.e Max number of nodes is 10, integer size is 4 bytes in java)
        public final static int MAX_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE; // bytes

	/* may consider adding more fields based on broadcast algorithm used */

	public int[] cost; // link state vector which contains cost to all nodes; actual cost if node is a neighbor; 999 (infinity) if node is not a neighbor

	
	
	 /**
	 * Constructor
	 * 
	 * Creates a link state message
        * @param sourceid	node id of source node (link state vector belongs to this node)
        * @param destid		id of destination node, this changes when a node forwards the message to its neighbor		
        * @param cost		link cost vector

	 */
	public LinkState(int sourceid, int destid, int[] cost) {
		this.sourceId = sourceid;
		this.destId = destid;
		setCost(cost);
	}
	

	/**
	* Constructor 
     	* 
     	* Creates a LinkState using the given byte array.
     	* It uses the byte array to reconstruct both the header and payload of the linkstate.
     	* 
     	* @param bytes      a byte array to set the header and payload of the linkstate
     	*/
        public LinkState(byte[] bytes) {
                setBytes(bytes);
        }
	
	/**
	* Copy constructor
	* 
	* @param ls	LinkState message

	*/
	public LinkState(LinkState ls) {
		this(ls.sourceId, ls.destId, ls.cost);
	}

      	/**
     	* Constructor 
     	* 
     	* Creates a LinkState message using the payload of the given DatagramPacket.
     	* It uses the data in the datagram packet to constructs both the header and payload of the linkstate
     	* 
     	* 
     	* @param packet    The data payload of the packet is used to initialize the linkstate
     	*/
        public LinkState(DatagramPacket packet) {
                this(Arrays.copyOf(packet.getData(), packet.getLength()));
        }


	/**
	* Returns a new copy of link state array
	* 
	*/
	public int[] getCost() {
		return Arrays.copyOf(cost, cost.length);
	}
	
	/**
	* Sets the link state array with a copy of the parameter lc
	* 
        * @param lc	link state array

	*/
	public void setCost(int[] lc) {
		this.cost = Arrays.copyOf(lc, lc.length);
	}


     	/**
     	* Returns the entire linkstate message as a byte array.
     	* The byte array contains both the header and the payload of the linkstate.
     	* Useful when creating a DatagramPacket to encapsulate a linkstate.  
     	* 
     	* @return A byte array containing the entire linkstate
     	*/
        public byte[] getBytes() {
                byte[] bytes = new byte[HEADER_SIZE + cost.length * Integer.SIZE/8];

                // store sequence number field 
                bytes[0] = (byte) (sourceId);
                bytes[1] = (byte) (sourceId >>> 8);
                bytes[2] = (byte) (sourceId >>> 16);
                bytes[3] = (byte) (sourceId >>> 24);


		bytes[4] = (byte) (destId);
                bytes[5] = (byte) (destId >>> 8);
                bytes[6] = (byte) (destId >>> 16);
                bytes[7] = (byte) (destId >>> 24);

	        // store payload data -- link state vector
                int baseindex = HEADER_SIZE; // in bytes
		int payloadsize = cost.length * Integer.SIZE/8; // in bytes
                int count = 0;
                for(int i = 0; i < payloadsize; i = i + 4)
                {

                        bytes[baseindex + i] = (byte) (cost[count]);
                        bytes[baseindex + i + 1] = (byte) (cost[count] >>> 8);
                        bytes[baseindex + i + 2] = (byte) (cost[count] >>> 16);
                        bytes[baseindex + i + 3] = (byte) (cost[count] >>> 24);
                        
			count++;
                }

                return bytes;
        }	
	

	/**
     	* Sets the content of a linkstate using the given byte array.
     	* It reconstructs both the header and payload of the linkstate.
     	* Useful when de-encapsulating a received DatagramPacket to a linkstate. 
     	* 
    	 * @param bytes The byte array used to set the header+payload of the linkstate
     	* 
     	* @throws IllegalArgumentException If the bytes array is too short to even recover the header 
     	*/
        public void setBytes(byte[] bytes) {
                // the header is REQUIRED
                if (bytes.length < HEADER_SIZE)
                        throw new IllegalArgumentException("Link state header missing");


                // construct the header fields
                int b0 = bytes[0] & 0xFF;
                int b1 = bytes[1] & 0xFF;
                int b2 = bytes[2] & 0xFF;
                int b3 = bytes[3] & 0xFF;
                sourceId = (b3 << 24) + (b2 << 16) + (b1 << 8) + (b0);

		b0 = bytes[4] & 0xFF;
                b1 = bytes[5] & 0xFF;
                b2 = bytes[6] & 0xFF;
                b3 = bytes[7] & 0xFF;
                destId = (b3 << 24) + (b2 << 16) + (b1 << 8) + (b0);


                // construct  payload -- link state vector
                int baseindex = HEADER_SIZE; // in bytes
	        int payloadsize = bytes.length - HEADER_SIZE;
                cost = new int[payloadsize/(Integer.SIZE/8)];

		if(payloadsize > MAX_PAYLOAD_SIZE)
			throw new IllegalArgumentException("More than maximum payload allowed"); 
		
		int count = 0;
		for(int i = 0; i < payloadsize; i = i + 4)
		{	
			
			b0 = bytes[baseindex + i] & 0xFF;
                	b1 = bytes[baseindex + i + 1] & 0xFF;
                	b2 = bytes[baseindex + i + 2] & 0xFF;
                	b3 = bytes[baseindex + i + 3] & 0xFF;
                	cost[count] = (b3 << 24) + (b2 << 16) + (b1 << 8) + (b0);
			
                	count++;
		}
        }
}
