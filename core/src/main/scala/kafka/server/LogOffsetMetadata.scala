/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.server

import kafka.log.Log
import org.apache.kafka.common.KafkaException

object LogOffsetMetadata {
  val UnknownOffsetMetadata = LogOffsetMetadata(-1, 0, 0)
  val UnknownFilePosition = -1

  class OffsetOrdering extends Ordering[LogOffsetMetadata] {
    override def compare(x: LogOffsetMetadata, y: LogOffsetMetadata): Int = {
      x.offsetDiff(y).toInt
    }
  }

}

/*
 * A log offset structure, including:
 *  1. the message offset
 *      消息位移
 *  2. the base message offset of the located segment
 *      保存该位移值所在日志段的起始位置（用于判断两条消息是否处于同一日志段）
 *  3. the physical position on the located segment
 *      该位移值所在日志段的物理磁盘位置（用于计算两个位移值之间物理磁盘
 *      的差值。读取日志的时候，例如只能读1MB，则关心两个位移之间的消息总
 *      字节数是否超过1MB）
 */
case class LogOffsetMetadata(messageOffset: Long,
                             segmentBaseOffset: Long = Log.UnknownOffset,
                             relativePositionInSegment: Int = LogOffsetMetadata.UnknownFilePosition) {

  // check if this offset is already on an older segment compared with the given offset
  // 是否在更老的段上
  def onOlderSegment(that: LogOffsetMetadata): Boolean = {
    if (messageOffsetOnly)
      throw new KafkaException(s"$this cannot compare its segment info with $that since it only has message offset info")

    this.segmentBaseOffset < that.segmentBaseOffset
  }

  // check if this offset is on the same segment with the given offset
  // 是否在相同的段上
  def onSameSegment(that: LogOffsetMetadata): Boolean = {
    if (messageOffsetOnly)
      throw new KafkaException(s"$this cannot compare its segment info with $that since it only has message offset info")

    this.segmentBaseOffset == that.segmentBaseOffset
  }

  // compute the number of messages between this offset to the given offset
  // 计算两个消息的位移值
  def offsetDiff(that: LogOffsetMetadata): Long = {
    this.messageOffset - that.messageOffset
  }

  // compute the number of bytes between this offset to the given offset
  // if they are on the same segment and this offset precedes the given offset
  // 计算两个消息的位移差值，前提是在同一个段上
  def positionDiff(that: LogOffsetMetadata): Int = {
    if(!onSameSegment(that))
      throw new KafkaException(s"$this cannot compare its segment position with $that since they are not on the same segment")
    if(messageOffsetOnly)
      throw new KafkaException(s"$this cannot compare its segment position with $that since it only has message offset info")

    this.relativePositionInSegment - that.relativePositionInSegment
  }

  // decide if the offset metadata only contains message offset info
  // 是否只含有消息偏移，segmentBaseOffset、relativePositionInSegment都是默认值
  def messageOffsetOnly: Boolean = {
    segmentBaseOffset == Log.UnknownOffset && relativePositionInSegment == LogOffsetMetadata.UnknownFilePosition
  }

  override def toString = s"(offset=$messageOffset segment=[$segmentBaseOffset:$relativePositionInSegment])"

}
