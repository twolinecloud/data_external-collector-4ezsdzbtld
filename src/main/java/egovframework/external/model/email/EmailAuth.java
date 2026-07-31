package egovframework.external.model.email;

import lombok.Data;

@Data
public class EmailAuth {
	String id;
	String password;
	String sessionId;
}
