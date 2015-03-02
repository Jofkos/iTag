package org.kitteh.tag.api;

public class TagAPIException extends RuntimeException {
	
	private static final long serialVersionUID = -3260514916532496435L;

	public TagAPIException(String message) {
		super(message);
	}

	public TagAPIException(String message, Throwable cause) {
		super(message, cause);
	}
}
