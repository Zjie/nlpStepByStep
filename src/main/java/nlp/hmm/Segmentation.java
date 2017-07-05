package nlp.hmm;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * generate hmm model (A, B, π)
 * 
 * @author zhoujie status set S = {Begin, Middle, End, Stop} emission symbol K =
 *         {all Chinese character}
 */
public class Segmentation {

	public final static char STATUS_B = 'B';
	public final static char STATUS_M = 'M';
	public final static char STATUS_E = 'E';
	public final static char STATUS_S = 'S';
	private HMMModel model;
	Logger logger;
	public Segmentation() {
		logger = Logger.getLogger(this.getClass().getName());
		model = new HMMModel();
	}
	
	public static void main(String[] args) {
		// String fn =
		// "C:\\Users\\zj125135\\Downloads\\语料库\\词性标注%40人民日报199801.txt";
		String fn = "C:\\Users\\zj125135\\Downloads\\语料库\\词性标注%40人民日报199801.txt";
		String output = "C:\\Users\\zj125135\\Downloads\\语料库\\model";
		Segmentation model = new Segmentation();
		model.generate(fn);
		model.outputModel(output);
		
		
//		GenModel model2 = new GenModel();
//		model2.loadModel(output);
//		System.out.println("load success");
	}

	static class CharStatus {
		char status; // B M E S
		char character; // a single Chinese character

		CharStatus(char s, char c) {
			status = s;
			character = c;
		}

		@Override
		public String toString() {
			return status + "[" + character + "]";
		}
	}

	public void generate(String input) {
		BufferedReader reader = null;
		InputStreamReader isr = null;
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(input);
			isr = new InputStreamReader(fis, "UTF-8");
			reader = new BufferedReader(isr);
			String line = null;
			int lineNum = 0;
			logger.info("begin to process data");
			while ((line = reader.readLine()) != null) {
				// transfer one line to status set
				// such as a sentence: "19980101-01-001-001/m 迈向/v 充满/v 希望/n 的/u
				// 新/a 世纪/n ——/w 一九九八年/t 新年/t 讲话/n （/w 附/v 图片/n １/m 张/q ）/w "
				// then we get "BE BE BE S S BE S BMMME BE BE S S BE S S S"
				List<CharStatus> status = generateStatusList(line);
				// iterate all status and update vector A, vector B
				updateAB(status);
				if (lineNum % 10000 == 0) {
					logger.info("line:" + lineNum);
				}
				lineNum++;
			}
			logger.info("normaliza vector A and vector B");
			normalizeAB();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e1) {
				}
			}
			if (isr != null) {
				try {
					isr.close();
				} catch (IOException e1) {
				}
			}
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e1) {
				}
			}
		}
	}

	private List<CharStatus> generateStatusList(String line) {
		// remove some useless character
		line = line.replaceAll("\\]\\w+", "").replace("\\", "");
		String[] spiltParts = line.split("/\\w+");

		List<CharStatus> status = new ArrayList<CharStatus>();

		for (int i = 1; i < spiltParts.length - 1; i++) {
			String s = spiltParts[i];
			char[] cr = s.replace(" ", "").toCharArray();
			if (cr.length == 0) {
				continue;
			} else if (cr.length == 1) {
				status.add(new CharStatus(STATUS_S, cr[0]));
			} else {
				for (int j = 0; j < cr.length; j++) {
					if (j == 0) {
						status.add(new CharStatus(STATUS_B, cr[j]));
					} else if (j == cr.length - 1) {
						status.add(new CharStatus(STATUS_E, cr[j]));
					} else {
						status.add(new CharStatus(STATUS_M, cr[j]));
					}
				}
			}
		}
		return status;
	}

	private void updateAB(List<CharStatus> status) {
		CharStatus lastStatus = null;
		for (CharStatus cur : status) {
			// update B
			int Bidx = -1;
			switch (cur.status) {
			case STATUS_B:
				Bidx = 0;
				break;
			case STATUS_M:
				Bidx = 1;
				break;
			case STATUS_E:
				Bidx = 2;
				break;
			case STATUS_S:
				Bidx = 3;
				break;
			default:
				break;
			}
			if (Bidx >= 0) {
				Map<String, Double> b = model.B.get(Bidx);
				String singleChar = cur.character + "";
				b.compute(singleChar, (k, v) -> (v == null) ? new Double(1.0) : v + 1);
			}

			// update A
			if (lastStatus == null) {
				lastStatus = cur;
				continue;
			}
			switch (cur.status) {
			case STATUS_B:
				if (lastStatus.status == STATUS_S) {
					// S-->B, update a00
					model.A[3][0]++;
				} else if (lastStatus.status == STATUS_E) {
					// E-->B
					model.A[2][0]++;
				}
				break;
			case STATUS_M:
				if (lastStatus.status == STATUS_M) {
					// M-->M
					model.A[1][1]++;
				} else if (lastStatus.status == STATUS_B) {
					// B-->M
					model.A[0][1]++;
				}
				break;
			case STATUS_E:
				if (lastStatus.status == STATUS_M) {
					// M-->E
					model.A[1][2]++;
				} else if (lastStatus.status == STATUS_B) {
					// B-->E
					model.A[0][2]++;
				}
				break;
			case STATUS_S:
				if (lastStatus.status == STATUS_S) {
					// S-->S
					model.A[3][3]++;
				} else if (lastStatus.status == STATUS_E) {
					// E-->S
					model.A[2][3]++;
				}
				break;
			default:
				break;
			}
			lastStatus = cur;
		}
	}

	private void normalizeAB() {
		for (Map<String, Double> b : model.B) {
			final long total = b.values().stream().mapToLong(i -> i.longValue()).sum();
			// normalize as probability
			b.replaceAll((k, v) -> v / total);
		}

		for (int i = 0; i < model.A.length; i++) {
			double total = 0;
			for (int j = 0; j < model.A[i].length; j++) {
				total = total + model.A[i][j];
			}
			for (int j = 0; j < model.A[i].length; j++) {
				// normalize as probability
				model.A[i][j] = model.A[i][j] / total;
			}
		}
	}

	public void outputModel(String output) {
		// just flush java object
		FileOutputStream outStream = null;
		try {
			outStream = new FileOutputStream(output);
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
			objectOutputStream.writeObject(this.model);
			logger.info("flush model success");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (outStream != null) {
					outStream.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void loadModel(String input) {
		FileInputStream freader = null;
		try {
			freader = new FileInputStream(input);
			ObjectInputStream objectInputStream = new ObjectInputStream(freader);
			HMMModel model = (HMMModel) objectInputStream.readObject();
			this.model = model;
			logger.info("load model success");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				if (freader != null)
					freader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
