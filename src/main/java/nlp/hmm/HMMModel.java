package nlp.hmm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HMMModel implements Serializable {

	private static final long serialVersionUID = -4348736157423653748L;
	double[][] A;
	// vector A is a state transition matrix
	//   B M E S
	// B 0 1 1 0
	// M 0 1 1 0
	// E 1 0 0 1
	// S 1 0 0 1
	List<Map<String, Double>> B;
	public HMMModel() {
		A = new double[4][4];
		B = new ArrayList<Map<String, Double>>(4);
		for (int i = 0; i < 4; i++) {
			B.add(new HashMap<String, Double>());
			for (int j = 0; j < 4; j++) {
				A[i][j] = 0;
			}
		}
	}
}