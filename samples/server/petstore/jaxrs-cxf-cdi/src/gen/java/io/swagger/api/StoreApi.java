package io.swagger.api;

import java.util.Map;
import io.swagger.model.Order;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.ext.multipart.*;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Path("/")
@Api(value = "/", description = "")
public interface StoreApi  {

    @DELETE
    @Path("/order/{orderId}")
    
    @Produces({ "application/xml", "application/json" })
    @ApiOperation(value = "Delete purchase order by ID", tags={ "store",  })
    public void  deleteOrder(@PathParam("orderId") String orderId);

    @GET
    @Path("/inventory")
    
    @Produces({ "application/json" })
    @ApiOperation(value = "Returns pet inventories by status", tags={ "store",  })
    public Integer  getInventory();

    @GET
    @Path("/order/{orderId}")
    
    @Produces({ "application/xml", "application/json" })
    @ApiOperation(value = "Find purchase order by ID", tags={ "store",  })
    public Order  getOrderById(@PathParam("orderId") Long orderId);

    @POST
    @Path("/order")
    
    @Produces({ "application/xml", "application/json" })
    @ApiOperation(value = "Place an order for a pet", tags={ "store" })
    public Order  placeOrder(Order body);
}
