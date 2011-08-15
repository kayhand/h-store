package edu.brown.benchmark.auctionmark.util;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.voltdb.catalog.Database;

import edu.brown.utils.JSONSerializable;
import edu.brown.utils.JSONUtil;

public abstract class CompositeId implements JSONSerializable {
    
    protected transient int hashCode = -1;
    
    protected final long encode(long max_value, long offset) {
        long values[] = this.toArray();
        assert(values.length == 2);
        long id = 0;
        for (int i = 0; i < values.length; i++) {
            assert(values[i] >= 0) : String.format("%s value at position %d is %d",
                                                   this.getClass().getSimpleName(), i, values[i]);
            assert(values[i] < max_value) : String.format("%s value at position %d is %d. Max value is %d",
                                                          this.getClass().getSimpleName(), i, values[i], max_value);
            id = (i == 0 ? values[i] : id | values[i]<<(offset * i));
        } // FOR
        return (id);
    }
    
    protected final long[] decode(long composite_id, long values[], long max_value, long offset) {
        for (int i = 0; i < values.length; i++) {
            values[i] = (composite_id>>(offset * i) & max_value);
        } // FOR
        return (values);
    }
    
    public abstract long encode();
    public abstract void decode(long composite_id);
    public abstract long[] toArray();
    
    @Override
    public int hashCode() {
        if (this.hashCode == -1) {
            this.hashCode = new Long(this.encode()).hashCode();
        }
        return (this.hashCode);
    }
    
    // -----------------------------------------------------------------
    // SERIALIZATION
    // -----------------------------------------------------------------
    
    @Override
    public void load(String input_path, Database catalog_db) throws IOException {
        JSONUtil.load(this, catalog_db, input_path);
    }
    @Override
    public void save(String output_path) throws IOException {
        JSONUtil.save(this, output_path);
    }
    @Override
    public String toJSONString() {
        return (JSONUtil.toJSONString(this));
    }
    @Override
    public void toJSON(JSONStringer stringer) throws JSONException {
        stringer.key("ID").value(this.encode());
    }
    @Override
    public void fromJSON(JSONObject json_object, Database catalog_db) throws JSONException {
        this.decode(json_object.getLong("ID"));
    }
}
