package de.bornemisza.rest;

import java.util.ArrayList;

import com.hazelcast.core.IList;
import com.hazelcast.core.ItemListener;

public class PseudoHazelcastList<T> extends ArrayList<T> implements IList<T> {

    @Override
    public String getName() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String addItemListener(ItemListener<T> il, boolean bln) {
        return "MyListener";
    }

    @Override
    public boolean removeItemListener(String string) {
        return true;
    }

    @Override
    public String getPartitionKey() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getServiceName() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void destroy() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
