
package io.intelliflow;

public class KubeEvent{
	
	String name;
	Integer port;
	
	public KubeEvent(){}
	
	public KubeEvent(String pName, Integer pPort){
		this.name = pName;
		this.port = pPort;
	}
	
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Integer getPort() {
		return port;
	}
	public void setPort(Integer port) {
		this.port = port;
	}
	
	
	
}