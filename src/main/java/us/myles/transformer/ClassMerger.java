package us.myles.transformer;

public class ClassMerger {
	private String a;
	private String b;

	public ClassMerger(String a, String b) {
		this.a = a;
		this.b = b;
	}

	public ClassMerger(Class a, Class b) {
		this.a = a.getName();
		this.b = b.getName();
	}

	public String getA() {
		return a;
	}

	public String getB() {
		return b;
	}
}
