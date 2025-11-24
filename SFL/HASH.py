"""
These are the standard parameters for this operation:
m1=13950851
m2=1036845127411
base=257
"""
def main():
    """ print(hash_two_stage(b"7h15_p455w0rd_15_p3rf3c71y_53cur3"))
    c = 0
    for b in b"7h15_p455w0rd_15_p3rf3c71y_53cur3":
        print(b)
        c+=1
    print(c)
    print(len("7h15_p455w0rd_15_p3rf3c71y_53cur3"))"""
    a = hash_two_stage(b"7")
    for i in numbers


def hash_two_stage(data: bytes, base=257, m1=13950851, m2=1036845127411):
    h = 0
    pow_b = 1
    for b in data:
        h = (h + b * pow_b) % m1
        pow_b = (pow_b * base) % m1
    return (h * h + 37 * h + 61261) % m2
if __name__ == "__main__":
    main()