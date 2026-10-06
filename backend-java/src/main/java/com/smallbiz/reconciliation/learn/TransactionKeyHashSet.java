package com.smallbiz.reconciliation.learn;

/**
 * TransactionKey를 배열과 연결 노드로 보관하는 학습용 집합.
 * java.util.HashSet / HashMap / LinkedList에 저장·충돌 처리를 맡기지 않는다.
 */
public final class TransactionKeyHashSet {

	private static final int INITIAL_CAPACITY = 16;
	private static final float LOAD_FACTOR = 0.75f;

	private Node[] table;
	private int size;

	public TransactionKeyHashSet() {
		this.table = new Node[INITIAL_CAPACITY];
		this.size = 0;
	}

	public boolean add(TransactionKey key) {
		if (contains(key)) {
			return false;
		}
		if (size + 1 > table.length * LOAD_FACTOR) {
			resize();
		}
		int index = bucketIndex(key);
		table[index] = new Node(key, table[index]);
		size++;
		return true;
	}

	public boolean contains(TransactionKey key) {
		if (key == null) {
			throw new IllegalArgumentException("key");
		}
		for (Node node = table[bucketIndex(key)]; node != null; node = node.next) {
			if (node.key.equals(key)) {
				return true;
			}
		}
		return false;
	}

	public int size() {
		return size;
	}

	int tableLength() {
		return table.length;
	}

	int bucketIndex(TransactionKey key) {
		return Math.floorMod(key.hashCode(), table.length);
	}

	int chainLength(TransactionKey key) {
		int count = 0;
		for (Node node = table[bucketIndex(key)]; node != null; node = node.next) {
			count++;
		}
		return count;
	}

	private void resize() {
		Node[] previous = table;
		table = new Node[previous.length * 2];
		for (Node head : previous) {
			Node node = head;
			while (node != null) {
				Node next = node.next;
				int index = Math.floorMod(node.key.hashCode(), table.length);
				node.next = table[index];
				table[index] = node;
				node = next;
			}
		}
	}

	private static final class Node {

		private final TransactionKey key;
		private Node next;

		private Node(TransactionKey key, Node next) {
			this.key = key;
			this.next = next;
		}
	}
}
