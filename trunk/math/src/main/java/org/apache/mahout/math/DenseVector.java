/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.math;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.mahout.math.function.BinaryFunction;
import org.apache.mahout.math.function.PlusMult;

/** Implements vector as an array of doubles */
public class DenseVector extends AbstractVector {

  private double[] values;

  /** For serialization purposes only */
  public DenseVector() {
    super(0);
  }

  /** Construct a new instance using provided values */
  public DenseVector(double[] values) {
    this(values, false);
  }

  public DenseVector(double[] values, boolean shallowCopy) {
    super(values.length);
    this.values = shallowCopy ? values : values.clone();
  }

  public DenseVector(DenseVector values, boolean shallowCopy) {
    this(values.values, shallowCopy);
  }

  /** Construct a new instance of the given cardinality */
  public DenseVector(int cardinality) {
    super(cardinality);
    this.values = new double[cardinality];
  }

  /**
   * Copy-constructor (for use in turning a sparse vector into a dense one, for example)
   * @param vector
   */
  public DenseVector(Vector vector) {
    super(vector.size());
    values = new double[vector.size()];
    Iterator<Element> it = vector.iterateNonZero();
    while (it.hasNext()) {
      Element e = it.next();
      values[e.index()] = e.get();
    }
  }

  @Override
  protected Matrix matrixLike(int rows, int columns) {
    return new DenseMatrix(rows, columns);
  }

  @Override
  public DenseVector clone() {
    return new DenseVector(values.clone());
  }

  /**
   * @return true
   */
  public boolean isDense() {
    return true;
  }

  /**
   * @return true
   */
  public boolean isSequentialAccess() {
    return true;
  }

  @Override
  public double dotSelf() {
    double result = 0.0;
    int max = size();
    for (int i = 0; i < max; i++) {
      double value = this.getQuick(i);
      result += value * value;
    }
    return result;
  }

  public double getQuick(int index) {
    return values[index];
  }

  public DenseVector like() {
    return new DenseVector(size());
  }

  public void setQuick(int index, double value) {
    lengthSquared = -1.0;
    values[index] = value;
  }
  
  @Override
  public Vector assign(double value) {
    this.lengthSquared = -1;
    Arrays.fill(values, value);
    return this;
  }
  
  @Override
  public Vector assign(Vector other, BinaryFunction function) {
    if (size() != other.size()) {
      throw new CardinalityException(size(), other.size());
    }
    // is there some other way to know if function.apply(0, x) = x for all x?
    if (function instanceof PlusMult) {
      Iterator<Element> it = other.iterateNonZero();
      Element e;
      while (it.hasNext() && (e = it.next()) != null) {
        values[e.index()] = function.apply(values[e.index()], e.get());
      }
    } else {
      for (int i = 0; i < size(); i++) {
        values[i] = function.apply(values[i], other.getQuick(i));
      }
    }
    lengthSquared = -1;
    return this;
  }

  public int getNumNondefaultElements() {
    return values.length;
  }

  @Override
  public Vector viewPart(int offset, int length) {
    if (offset < 0) {
      throw new IndexException(offset, size());
    }
    if (offset + length > size()) {
      throw new IndexException(offset + length, size());
    }
    return new VectorView(this, offset, length);
  }

  /**
   * Returns an iterator that traverses this Vector from 0 to cardinality-1, in that order.
   */
  public Iterator<Element> iterateNonZero() {
    return new NonDefaultIterator();
  }

  public Iterator<Element> iterator() {
    return new AllIterator();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof DenseVector) {
      // Speedup for DenseVectors
      return Arrays.equals(values, ((DenseVector) o).values);
    }
    return super.equals(o);
  }

  @Override
  public double getLengthSquared() {
    if (lengthSquared >= 0.0) {
      return lengthSquared;
    }

    double result = 0.0;
    for (double value : values) {
      result += value * value;

    }
    lengthSquared = result;
    return result;
  }

  @Override
  public void addTo(Vector v) {
    if (size() != v.size()) {
      throw new CardinalityException(size(), v.size());
    }
    for (int i = 0; i < values.length; i++) {
      v.setQuick(i, values[i] + v.getQuick(i));
    }
  }
  
  public void addAll(Vector v) {
    if (size() != v.size()) {
      throw new CardinalityException(size(), v.size());
    }
    
    Iterator<Element> iter = v.iterateNonZero();
    while (iter.hasNext()) {
      Element element = iter.next();
      values[element.index()] += element.get();
    }
  }

  
  @Override
  public double dot(Vector x) {
    if (size() != x.size()) {
      throw new CardinalityException(size(), x.size());
    }
    if (this == x) {
      return dotSelf();
    }
    
    double result = 0;
    if (x instanceof DenseVector) {
      for (int i = 0; i < x.size(); i++) {
        result += this.values[i] * x.getQuick(i);
      }
      return result;
    } else {
      // Try to get the speed boost associated fast/normal seq access on x and quick lookup on this
      Iterator<Element> iter = x.iterateNonZero();
      while (iter.hasNext()) {
        Element element = iter.next();
        result += element.get() * this.values[element.index()];
      }
      return result;
    }
  }


  private final class NonDefaultIterator implements Iterator<Element> {

    private final DenseElement element = new DenseElement();
    private int index = 0;

    private NonDefaultIterator() {
      goToNext();
    }

    private void goToNext() {
      while (index < size() && values[index] == 0.0) {
        index++;
      }
    }

    public boolean hasNext() {
      return index < size();
    }

    public Element next() {
      if (index >= size()) {
        throw new NoSuchElementException();
      } else {
        element.index = index;
        index++;
        goToNext();
        return element;
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private final class AllIterator implements Iterator<Element> {

    private final DenseElement element = new DenseElement();

    private AllIterator() {
      element.index = -1;
    }

    public boolean hasNext() {
      return element.index + 1 < size();
    }

    public Element next() {
      if (element.index + 1 >= size()) {
        throw new NoSuchElementException();
      } else {
        element.index++;
        return element;
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private final class DenseElement implements Element {

    int index;

    public double get() {
      return values[index];
    }

    public int index() {
      return index;
    }

    public void set(double value) {
      lengthSquared = -1;
      values[index] = value;
    }
  }

}