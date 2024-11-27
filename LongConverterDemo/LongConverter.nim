import std/[times]

# This is just an example to get you started. A typical binary package
# uses this file as the main entry point of the application.

type
    MemberMap = object
        dimensionPosition: int
        carry: uint64
        minValue: uint32
        maxValue: uint32

    LongConverter = ref object
        fixedMembers: seq[uint32]
        mappings: seq[MemberMap]
    
func newMemberMap(dimensionPosition: int, members: seq[uint32], carry: uint64): MemberMap =
    result.dimensionPosition = dimensionPosition
    result.carry = carry
    result.minValue = members.min()
    result.maxValue = members.max()

func step(this: MemberMap): uint64 =
  (this.maxValue.uint64 - this.minValue.uint64) + 1.uint64

func toX(this:MemberMap, y: openArray[uint32]): uint64 =
  return this.carry * (y[this.dimensionPosition] - this.minValue)

proc newLongConverter(dimensionInfo: seq[seq[uint32]]) : LongConverter=
  result = LongConverter()
  result.fixedMembers = newSeq[uint32](dimensionInfo.len)
  result.mappings = newSeq[MemberMap]()
  var carry = 1.uint64

  for i,dim in dimensionInfo:
    case dim.len
    of 0:
      raise newException(ValueError,"not support 0")
    of 1:
      result.fixedMembers[i] = dim[0]
    else:
      let map = newMemberMap(i, dim, carry)
      if map.step == 0:
        result.fixedMembers[i] = map.minValue
      else:
        carry *= map.step
        result.mappings.add(map)

func toX(this: LongConverter, y: openArray[uint32]): uint64 =
  result = 0
  for mapping in this.mappings:
    result += mapping.toX(y)
  return result

func getDimensionInfo(size : int) : seq[seq[uint32]] =
  return case size
    of 1:
      @[
        @[900],
        @[0, 1, 9, 5],    #min = 0, max: 9
        @[900],@[900],@[900],

        @[900],@[900],@[900],@[900],@[900],

        @[900],
      ]
    else:
      @[]
      #raise newExpcetion(ValueError,"size")

proc performanceTestCore(name: string, target: LongConverter) =
  var key = @[9u32, 5, 900, 832, 67, 800, 100_0000, 1000_0001, 3, 4, 9]
  var x = 0.uint64
  let startTime = cpuTime()

  for i in 0 ..< int32.high:
    key[0] = i.uint32
    x += target.toX(key)

  let endTime = cpuTime()
  echo name, " time = ", endTime - startTime, " result = ", x

proc performanceTest() =
  for i in 1 ..< 2:
    performanceTestCore("basic:", newLongConverter(getDimensionInfo(i)))

when isMainModule:
  echo("Hello, World!")
  performanceTest()