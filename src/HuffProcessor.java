import java.util.PriorityQueue;

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
		int[] counts = readForCounts(in); //helper method
		HuffNode root = makeTreeFromCounts(counts); //helper method
		String[] codings = new String[ALPH_SIZE+1];
		makeCodingsFromTree(root, codings, ""); //helper method

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out); //helper method

		in.reset();
		writeCompressedBits(codings,in,out); //helper method
		out.close();
	}

	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE+1];

		freq[PSEUDO_EOF] = 1;
		while(true) {
			int value = in.readBits(BITS_PER_WORD);
			if(value == -1) break;
			freq[value]++;
		}
		return freq;
	}

	private HuffNode makeTreeFromCounts(int[] counts){
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();

		for(int i = 0; i< counts.length; i++){
			if(counts[i] <= 0) continue;
			pq.add(new HuffNode(i, counts[i], null, null));
		}
		while (pq.size() > 1){
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	private void makeCodingsFromTree(HuffNode root, String codings[], String path) {
		if (root == null) return;

		if (root.myLeft == null && root.myRight == null) {
			codings[root.myValue] = path;
		} else {
			makeCodingsFromTree(root.myLeft, codings, path + "0");
			makeCodingsFromTree(root.myRight, codings, path + "1");
		}
	}

	private void writeHeader(HuffNode root, BitOutputStream out){
		if(root.myLeft != null && root.myRight != null){
			out.writeBits(1,0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else if (root.myLeft == null && root.myRight == null){
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out){
		while(true){
			int bit = in.readBits(BITS_PER_WORD);
			if(bit == -1) break;
			String code = codings[bit];
			out.writeBits(code.length(), Integer.parseInt(codings[PSEUDO_EOF], 2));
		}
		out.writeBits(codings[PSEUDO_EOF].length(), Integer.parseInt(codings[PSEUDO_EOF], 2));
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

		int magic = in.readBits(BITS_PER_INT);
		if (magic != HUFF_TREE) {
			throw new HuffException("invalid magic number "+magic);
		}
		HuffNode root = readTree(in);
		HuffNode current = root;
		// remove all code below this point for P7
		while(true){
			int bits = in.readBits(1);
			if(bits == -1){
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else{
				if(bits == 0) {
					current = current.myLeft;
				}
				else {
					current = current.myRight;
				}

				if(current.myLeft == null && current.myRight == null) {
					if(current.myValue == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}

		out.close();
	}
	private HuffNode readTree(BitInputStream in){
		int bit = in.readBits(1);
		if(bit == -1) throw new HuffException("no bits to read");
		if(bit == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0,0,left,right);
		}
		else{
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value,0,null,null);
		}
	}
}