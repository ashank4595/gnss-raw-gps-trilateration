
def findRadii():
    ReceiverTimeNanos = int(input("enter Receiver timeNanos: "))
    FullBiasNanos = int(input("enter FullBiasNanos: "))
    Trx = ReceiverTimeNanos - FullBiasNanos
    Tweek = 604800 * 1_000_000_000  # ns in a GPS week
    Tmod = Trx % Tweek

    print("Trx =", Trx)
    print("Tweek =", Tweek)
    print("Tmod =", Tmod)


    n = int(input("enter number of satellites: "))
    radii = {}

    for i in range(int(n)):
        currSat = int(input("enter satellite SVID/PRN number: "))
        recvsvTimeNanos = int(input("enter recvsvTimeNanos for SVID " + str(currSat) + " : ")) 
        deltaT = Tmod - recvsvTimeNanos
        radii[currSat] = 299_792_458 * deltaT #speed of light (m/s) * time difference (s)
    return radii

def findSatelliteCoordinates():
    return 

radii = findRadii()
print(radii)

satCoordinates = findSatelliteCoordinates()




