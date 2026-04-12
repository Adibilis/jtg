package ch.adibilis.jtg.model.endpoints;

public class PagedEndpoint extends Endpoint {

    private String pageVariable;
    private String pageSizeVariable;

    public String getPageVariable() { return pageVariable; }
    public void setPageVariable(String pageVariable) { this.pageVariable = pageVariable; }
    public String getPageSizeVariable() { return pageSizeVariable; }
    public void setPageSizeVariable(String pageSizeVariable) { this.pageSizeVariable = pageSizeVariable; }
}
