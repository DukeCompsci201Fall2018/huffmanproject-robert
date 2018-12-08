import java.util.PriorityQueue;

//Robert Chen
//Min Soo Kim
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[]  counts =  readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	/**
	 * 
	 * @param codings
	 * @param in
	 * 	 Buffered bit stream of the file to be compressed
	 * @param out
	 * 	Buffered bit stream writing to the output file.
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		boolean def = true;
		while (def) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				break;
			}
			else {
				String code = codings[bits];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
				
			}
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));

	}
	
	/**
	 * 
	 * @param in
	 * 	Buffered bit stream of the file to be compressed
	 * @return
	 * 	a string array of ALPH_sIZE + 1 lenght
	 */
	
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		boolean def = true;
		while(def) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				def = false;
				break;
			}
			freq[bits] = freq[bits] + 1;
		}
		freq[PSEUDO_EOF] = 1;
		return freq;
	}
	/**
	 * 
	 * @param freq
	 * 	a string array
	 * @return
	 * 	a huffnode with the vlaues within in freq
	 */
	private HuffNode makeTreeFromCounts(int[] freq) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();


		for (int index = 0; index < freq.length; index++) {
			if (freq[index] > 0) {
				pq.add(new HuffNode(index,freq[index],null,null));
			}
		}
		while (pq.size() > 1) {
		    HuffNode left = pq.remove();
		    HuffNode right = pq.remove();
		    HuffNode t = new HuffNode(-1, left.myWeight + right.myWeight, left, right);
		    // create new HuffNode t with weight from
		    // left.weight+right.weight and left, right subtrees
		    if (t.myWeight > 0) {
		    		pq.add(t);
		    }
		    //pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	/**
	 * 
	 * @param root
	 * 	a huffnode root to traverse through
	 * @return
	 * 	a string array that holds the pats for the different leafs
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
	    codingHelper(root,"",encodings);

		
		return encodings;
	}
	/**
	 * 
	 * @param root
	 * 	A huffnode root
	 * @param path
	 * 	A string that shows what the path of the root is from all the way from the top
	 * @param encodings
	 * 	A string array that holds all the paths
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		
		HuffNode left = root.myLeft;
		HuffNode right = root.myRight;
		if (left != null) {
			String pa = path + "0";
			codingHelper(left, pa, encodings);
		}
		if (right != null) {
			String pa = path + "1";
			codingHelper(right, pa, encodings);
		}
		
	}
	/**
	 * 
	 * @param root
	 * 	A huffnode root
	 * @param out
	 * 	Buffered bit stream of the file to be compressed
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft != null || root.myRight != null) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
		
	}
	
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}
	/**
	 * 
	 * @param in
	 * 	Buffered bit stream of the file to be decompressed.
	 * @return
	 * 	return a tree consisting of values within bitinputstream
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("reading bits failed");
		}
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}
	}
	/**
	 * 
	 * @param root
	 * 	the HuffMan root that holds all the values
	 * @param in
	 * 	Buffered bit stream of the file to be decompressed.
	 * @param out
	 * 	Buffered bit stream writing to the output file
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) { 
					current = current.myLeft;
				}
				else{
					current = current.myRight;
				}
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}
