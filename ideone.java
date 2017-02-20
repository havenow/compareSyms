import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Ideone {
	
	static boolean PROCESS_CPLUSPLUS = true;
	static boolean PRINT_CPLUSPLUS_SYMBOL = false;
	
	static ArrayList<File> fileList = new ArrayList<File>();
	static ArrayList<HashMap<String, ArrayList<String>>> symList = new ArrayList<HashMap<String, ArrayList<String>>>();
	static String[] excludeSymbolPrefix = {"___clang", "_llvm", "l_OBJC", "__ZT", "__ZNS", "__ZNKS"};
	static String[] excludeFunctionPrefix = {"_unz", "_zip"};
	static String[] excludeCplusplusClass = {"cocos2d", "tinyxml2", "rapidjson"};
	static String[] excludeCplusplusTag = {"INS"};
	static String folderPath = "C:\\Users\\luzy\\Desktop\\sym";
	static BufferedWriter writer;
	
	static void writeOutput(String str) throws IOException {
		System.out.print(str);
		writer.write(str);
	}
	
	public static void main(String[] args) throws IOException {
		writer = new BufferedWriter(new FileWriter(folderPath + "\\result.txt"));

		writeOutput(">>>DupSym<<<\n");
		writeOutput("run \"nm -g libtest.[so|a] > test.txt\" to get symbol file\n");
		
		File folder = new File(folderPath);
		
		for(File f : folder.listFiles()) {
			if(f.getName().endsWith(".txt")) {
				fileList.add(f);
				processFile(f);
			}
		}
		
		writeOutput("\n");

		for(int i = 0; i < fileList.size(); i++) {
			for(int j = i + 1; j < fileList.size(); j++) {
				compareSyms(i, j);
			}
		}
		
		writer.close();
	}
	
	static boolean excludeSymbol(String str) {
		for(String s : excludeSymbolPrefix) {
			if(str.startsWith(s)) {
				return true;
			}
		}
		for(String s : excludeFunctionPrefix) {
			if(str.startsWith(s)) {
				return true;
			}
		}
		if(!PROCESS_CPLUSPLUS && str.startsWith("__Z")) {
			return true;
		}
		return false;
	}

	static void processFile(File file) {
		System.out.println("Processing " + file + " ...");
		
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String line = null;
			String objName = "";
			HashMap<String, ArrayList<String>> h = new HashMap<String, ArrayList<String>>();
			while((line = reader.readLine()) != null) {
				Pattern pattern = Pattern.compile(".*\\((.*\\.o)\\).*");
				Matcher matcher = pattern.matcher(line);
				if(matcher.find()) {
					objName = matcher.group(1);
				}
				
				pattern = Pattern.compile("\\w+ [TS] (.*+)");
				matcher = pattern.matcher(line);
				if(matcher.find()) {
					String symbolName = matcher.group(1);
					if(excludeSymbol(symbolName)) {
						continue;
					}
					if(!h.containsKey(symbolName)) {
						h.put(symbolName, new ArrayList<String>());
					}
					if(!h.get(symbolName).contains(objName)) {
						h.get(symbolName).add(objName);
					}
				}
			}
			
			symList.add(h);
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	static String getLibrary(File file) {
		return file.getName().replace(".txt", "").toUpperCase();
	}
	
	static boolean excludeClass(String str) {
		for(String s : excludeCplusplusClass) {
			if(str.equals(s)) {
				return true;
			}
		}
		return false;
	}
	
	static boolean excludeTag(String str) {
		for(String s : excludeCplusplusTag) {
			if(str.startsWith(s)) {
				return true;
			}
		}
		return false;
	}
	
	static String getDemangledCPlusplusSymbol(String str) {
		String symbol = "";
		String length = "";
		boolean startFlag = false;
		boolean isNum = false;
		int count = 0;

		int i = 0;
		while(i < str.length()) {
			char c = str.charAt(i);
			if(c >= '0' && c <= '9') {
				length += c;
				isNum = true;
				startFlag = true;
			} else {
				if(isNum) {
					int len = Integer.parseInt(length);
					length = "";

					symbol = str.substring(i, i + len);
					if(count == 0 && excludeClass(symbol)) {
						return null;
					}

					symbol += "::";

					isNum = false;
					i += len;
					count++;
					continue;
				} else if(startFlag) {
					if(c == 'C') {
						symbol += "_Constructor";
						count++;
					} else if(c == 'D') {
						symbol += "_Deconstructor";
						count++;
					} else if(str.substring(i).startsWith("INS")) {
						return null;
					}
					break;
				}
			}
			i++;
		}

		if(count == 1) {
			symbol = "::" + symbol;
		}
		while(symbol.endsWith(":")) {
			symbol = symbol.substring(0, symbol.length() - 1);
		}

		return symbol;
	}
	
	static String getDemangledSymbol(String str) {
		String symbol = "";
		if(str.startsWith("__Z")) {
			String cppSymbol = getDemangledCPlusplusSymbol(str);
			if(cppSymbol == null) {
				return null;
			} else {
				symbol = "[C++] <" + cppSymbol + ">";
				if(PRINT_CPLUSPLUS_SYMBOL) {
					symbol += " (" + str + ")";
				}
			}
		} else {
			if(str.startsWith("_")) {
				symbol = "[C] <" + str.substring(1) + ">";
			} else {
				symbol = "[C] <" + str + ">";
			}
		}
		return symbol;
	}
	
	static void compareSyms(int file1, int file2) throws IOException {
		HashMap<String, ArrayList<String>> map1 = symList.get(file1);
		HashMap<String, ArrayList<String>> map2 = symList.get(file2);
		
		for(HashMap.Entry<String, ArrayList<String>> entry1 : map1.entrySet()) {
			for(HashMap.Entry<String, ArrayList<String>> entry2 : map2.entrySet()) {
				String str1 = entry1.getKey();
				String str2 = entry2.getKey();
				if(str1.equals(str2)) {
					String demangledSymbol = getDemangledSymbol(str1);
					if(demangledSymbol == null) {
						continue;
					}

					writeOutput(demangledSymbol + " in " + getLibrary(fileList.get(file1)) + " and " + getLibrary(fileList.get(file2)) + "\n");
					writeOutput(getLibrary(fileList.get(file1)) + " : ");
					int i = 0;
					for(String s : entry1.getValue()) {
						writeOutput(s + (i == entry1.getValue().size() - 1 ? "" : ", "));
						i++;
					}
					writeOutput("\n" + getLibrary(fileList.get(file2)) + " : ");
					i = 0;
					for(String s : entry2.getValue()) {
						writeOutput(s + (i == entry2.getValue().size() - 1 ? "" : ", "));
						i++;
					}
					writeOutput("\n\n");
					continue;
				}
			}
		}
	}
}
